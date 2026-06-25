package org.shakvilla.beatzmedia.platform.adapter.out.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import jakarta.enterprise.inject.Instance;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.adapter.out.scheduler.AdvisoryLockService.LockHandle;
import org.shakvilla.beatzmedia.platform.application.port.in.ScheduledJob;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Integration tests for {@link SchedulerRegistry} and {@link AdvisoryLockService} using a real
 * Postgres instance (Testcontainers). Proves LLFR-PLATFORM-01.2 AC: exactly-once semantics via
 * Postgres advisory locks even under concurrent tick simulation.
 *
 * <h2>Tests</h2>
 * <ol>
 *   <li>Advisory lock: {@code pg_try_advisory_lock} acquired — handle is returned.
 *   <li>Advisory lock: a second connection cannot acquire a lock held by the first (cross-session).
 *   <li>Advisory lock: after closing the handle (release), the lock can be re-acquired.
 *   <li>Registry: job runs exactly once when one of two "instances" wins the lock.
 *   <li>Registry: job runs exactly once across N concurrent threads simulating N nodes.
 *   <li>Registry: lock is released on job success (subsequent tick can acquire).
 *   <li>Registry: lock is released on job exception (subsequent tick can acquire).
 *   <li>Registry: {@code pg_locks} view shows no dangling advisory locks after a completed tick.
 * </ol>
 *
 * <p><strong>Migration note:</strong> Postgres advisory locks need no DDL — they are transient
 * session-level state. No Flyway migration is required for this infrastructure. ADD §5.2.
 *
 * <p><strong>Why advisory locks guard cross-process correctly:</strong> Each call to
 * {@link AdvisoryLockService#tryAcquire(long)} opens a dedicated JDBC connection and holds it open
 * inside the returned {@link LockHandle}. When closed, the connection is returned to the pool and
 * the lock is released. In a multi-node cluster each node has its own connection pool, so the
 * session-level lock is truly exclusive across nodes.
 */
@Testcontainers
@Tag("integration")
class SchedulerRegistryIT {

  @Container
  static final PostgreSQLContainer<?> PG =
      new PostgreSQLContainer<>("postgres:16-alpine");

  private static SimpleDataSource dataSource;

  @BeforeAll
  static void startDb() {
    PG.start();
    dataSource = new SimpleDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
  }

  @AfterAll
  static void stopDb() {
    PG.stop();
  }

  private AdvisoryLockService lockService;
  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    lockService = new AdvisoryLockService();
    lockService.dataSource = dataSource;
    meterRegistry = new SimpleMeterRegistry();
  }

  // -------------------------------------------------------------------------
  // Advisory-lock primitives
  // -------------------------------------------------------------------------

  @Test
  void advisoryLock_tryAcquire_returnsHandle_onSuccess() throws Exception {
    long key = AdvisoryLockService.lockKeyFor("it.acquire.test");

    Optional<LockHandle> handle = lockService.tryAcquire(key);
    assertTrue(handle.isPresent(), "Lock must be acquired on first attempt (no contention)");
    handle.get().close();
  }

  @Test
  void advisoryLock_secondConnection_cannotAcquire_whileFirstHolds() throws Exception {
    // Simulates two different Postgres sessions (different JVMs / connection pools).
    // Connection A holds the lock; connection B must fail to acquire.
    long key = AdvisoryLockService.lockKeyFor("it.cross-session");

    // A acquires and keeps the handle open (connection stays alive)
    Optional<LockHandle> handleA = lockService.tryAcquire(key);
    assertTrue(handleA.isPresent(), "Connection A must acquire the lock");

    try {
      // B tries via a raw connection to simulate a completely different session
      try (Connection connB = DriverManager.getConnection(
          PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
        boolean acquiredB = rawTryAcquire(connB, key);
        assertFalse(acquiredB, "Connection B must NOT acquire a lock held by connection A");
      }
    } finally {
      handleA.get().close();
    }

    // After A releases, a third attempt must succeed
    Optional<LockHandle> handleC = lockService.tryAcquire(key);
    assertTrue(handleC.isPresent(), "Lock must be re-acquirable after A releases");
    handleC.get().close();
  }

  @Test
  void advisoryLock_afterRelease_canBeReacquired() throws Exception {
    long key = AdvisoryLockService.lockKeyFor("it.reacquire");

    Optional<LockHandle> h1 = lockService.tryAcquire(key);
    assertTrue(h1.isPresent());
    h1.get().close();

    Optional<LockHandle> h2 = lockService.tryAcquire(key);
    assertTrue(h2.isPresent(), "Lock must be re-acquirable after close/release");
    h2.get().close();
  }

  // -------------------------------------------------------------------------
  // Registry: exactly-once dispatch
  // -------------------------------------------------------------------------

  @Test
  void registry_jobRunsExactlyOnce_whenOneOfTwoNodesWinsLock() throws Exception {
    // Demonstrates LLFR-PLATFORM-01.2 AC: go-live publishes exactly once.
    // Simulated: two registry instances for the same job compete; one pre-holds the advisory lock
    // (simulating "node A is already running this tick"), the other skips.
    AtomicInteger runCount = new AtomicInteger();
    ScheduledJob job = new ScheduledJob() {
      @Override public String jobName() { return "catalog.go-live"; }
      @Override public void runOnce() { runCount.incrementAndGet(); }
    };

    long key = AdvisoryLockService.lockKeyFor("catalog.go-live");

    // Node A pre-acquires via a raw connection (simulates another node already running)
    try (Connection nodeA = DriverManager.getConnection(
        PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
      boolean heldByA = rawTryAcquire(nodeA, key);
      assertTrue(heldByA, "Node A must hold the lock");

      // Node B tries via SchedulerRegistry — must skip
      AdvisoryLockService lockB = new AdvisoryLockService();
      lockB.dataSource = dataSource;
      SchedulerRegistry registryB = buildRegistry(lockB, job);
      registryB.runWithLock("catalog.go-live");

      assertEquals(0, runCount.get(), "Job must NOT run when lock is held by another node");
      double skipCount = meterRegistry.counter("scheduler.job.skip", "job", "catalog.go-live").count();
      assertEquals(1.0, skipCount, "Skip counter must be incremented");

      // Node A "finishes" — releases lock
      rawRelease(nodeA, key);

      // Now node B can run
      registryB.runWithLock("catalog.go-live");
      assertEquals(1, runCount.get(), "Job must run exactly once after node A releases the lock");
    }
  }

  @Test
  void registry_jobRunsExactlyOnce_underConcurrentThreads() throws Exception {
    // Stress test: N threads (simulating N cluster nodes) concurrently call runWithLock.
    // Advisory lock ensures exactly one thread wins and the job runs exactly once.
    int nodeCount = 8;
    AtomicInteger runCount = new AtomicInteger();
    ScheduledJob job = new ScheduledJob() {
      @Override public String jobName() { return "stress.test.job"; }
      @Override public void runOnce() {
        runCount.incrementAndGet();
        // Hold the lock briefly so other threads have a chance to contend
        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
      }
    };

    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done  = new CountDownLatch(nodeCount);
    ExecutorService pool = Executors.newFixedThreadPool(nodeCount);

    for (int i = 0; i < nodeCount; i++) {
      // Each "node" gets its own AdvisoryLockService with its own DataSource wrapper
      // (each call to tryAcquire opens a brand-new dedicated connection)
      AdvisoryLockService ls = new AdvisoryLockService();
      ls.dataSource = dataSource;
      SchedulerRegistry r = buildRegistry(ls, job);
      pool.submit(() -> {
        try {
          start.await();
          r.runWithLock("stress.test.job");
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          done.countDown();
        }
      });
    }

    start.countDown();  // Release all threads simultaneously
    assertTrue(done.await(30, TimeUnit.SECONDS), "All threads must complete within 30 s");
    pool.shutdown();

    assertEquals(1, runCount.get(),
        "Job must run exactly once across " + nodeCount + " concurrent threads (advisory lock)");
  }

  @Test
  void registry_lockIsReleased_afterSuccessfulRun() throws Exception {
    AtomicInteger runCount = new AtomicInteger();
    ScheduledJob job = new ScheduledJob() {
      @Override public String jobName() { return "release.success.job"; }
      @Override public void runOnce() { runCount.incrementAndGet(); }
    };
    SchedulerRegistry r = buildRegistry(lockService, job);

    r.runWithLock("release.success.job");
    assertEquals(1, runCount.get());

    // Lock must be free — we can acquire it again
    long key = AdvisoryLockService.lockKeyFor("release.success.job");
    Optional<LockHandle> h = lockService.tryAcquire(key);
    assertTrue(h.isPresent(), "Lock must be free after a successful job run");
    h.get().close();
  }

  @Test
  void registry_lockIsReleased_afterJobException() throws Exception {
    ScheduledJob failingJob = new ScheduledJob() {
      @Override public String jobName() { return "release.failure.job"; }
      @Override public void runOnce() { throw new RuntimeException("simulated failure"); }
    };
    SchedulerRegistry r = buildRegistry(lockService, failingJob);

    r.runWithLock("release.failure.job");  // must not throw to caller

    long key = AdvisoryLockService.lockKeyFor("release.failure.job");
    Optional<LockHandle> h = lockService.tryAcquire(key);
    assertTrue(h.isPresent(), "Lock must be free after a job exception");
    h.get().close();
  }

  @Test
  void advisoryLock_noDanglingLocks_inPgLocksAfterRelease() throws Exception {
    // Verify via pg_locks that no advisory lock lingers after the handle is closed.
    long key = AdvisoryLockService.lockKeyFor("cleanup.check");
    int upperBits = (int) (key >> 32);
    int lowerBits = (int) (key & 0xFFFFFFFFL);

    Optional<LockHandle> handle = lockService.tryAcquire(key);
    assertTrue(handle.isPresent());

    // Verify lock appears in pg_locks while held
    try (Connection inspector = DriverManager.getConnection(
        PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
      int countWhileHeld = advisoryLockCount(inspector, upperBits, lowerBits);
      assertTrue(countWhileHeld >= 1, "Lock must appear in pg_locks while handle is open");
    }

    handle.get().close();

    // After close, pg_locks must show no entry for this key
    try (Connection inspector = DriverManager.getConnection(
        PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
      int countAfterRelease = advisoryLockCount(inspector, upperBits, lowerBits);
      assertEquals(0, countAfterRelease,
          "No advisory lock must remain in pg_locks after the handle is closed");
    }
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private SchedulerRegistry buildRegistry(AdvisoryLockService ls, ScheduledJob... jobs) {
    SchedulerRegistry r = new SchedulerRegistry();
    r.lockService   = ls;
    r.meterRegistry = meterRegistry;
    r.jobBeans      = new FakeInstance(List.of(jobs));
    return r;
  }

  private static boolean rawTryAcquire(Connection conn, long key) throws SQLException {
    try (PreparedStatement ps = conn.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
      ps.setLong(1, key);
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getBoolean(1);
      }
    }
  }

  private static void rawRelease(Connection conn, long key) throws SQLException {
    try (PreparedStatement ps = conn.prepareStatement("SELECT pg_advisory_unlock(?)")) {
      ps.setLong(1, key);
      ps.executeQuery();
    }
  }

  private static int advisoryLockCount(Connection conn, int upperBits, int lowerBits)
      throws SQLException {
    try (PreparedStatement ps = conn.prepareStatement(
        "SELECT COUNT(*) FROM pg_locks WHERE locktype='advisory' "
            + "AND classid=? AND objid=? AND granted=true")) {
      ps.setInt(1, upperBits);
      ps.setInt(2, lowerBits);
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getInt(1);
      }
    }
  }

  // -------------------------------------------------------------------------
  // Minimal fakes
  // -------------------------------------------------------------------------

  @SuppressWarnings("all")
  private static class FakeInstance implements Instance<ScheduledJob> {
    private final List<ScheduledJob> jobs;
    FakeInstance(List<ScheduledJob> jobs) { this.jobs = jobs; }
    @Override public java.util.Iterator<ScheduledJob> iterator() { return jobs.iterator(); }
    @Override public ScheduledJob get() { throw new UnsupportedOperationException(); }
    @Override public Instance<ScheduledJob> select(java.lang.annotation.Annotation... q) { throw new UnsupportedOperationException(); }
    @Override public <U extends ScheduledJob> Instance<U> select(Class<U> s, java.lang.annotation.Annotation... q) { throw new UnsupportedOperationException(); }
    @Override public <U extends ScheduledJob> Instance<U> select(jakarta.enterprise.util.TypeLiteral<U> s, java.lang.annotation.Annotation... q) { throw new UnsupportedOperationException(); }
    @Override public boolean isUnsatisfied() { return jobs.isEmpty(); }
    @Override public boolean isAmbiguous() { return false; }
    @Override public void destroy(ScheduledJob i) {}
    @Override public jakarta.enterprise.inject.Instance.Handle<ScheduledJob> getHandle() { throw new UnsupportedOperationException(); }
    @Override public Iterable<? extends jakarta.enterprise.inject.Instance.Handle<ScheduledJob>> handles() { throw new UnsupportedOperationException(); }
    @Override public boolean isResolvable() { return !jobs.isEmpty(); }
  }

  /** Simple DataSource wrapper that creates a fresh connection per call. */
  private static class SimpleDataSource implements DataSource {
    private final String url, user, password;
    SimpleDataSource(String url, String user, String password) {
      this.url = url; this.user = user; this.password = password;
    }
    @Override public Connection getConnection() throws SQLException {
      return DriverManager.getConnection(url, user, password);
    }
    @Override public Connection getConnection(String u, String p) throws SQLException {
      return DriverManager.getConnection(url, u, p);
    }
    @Override public java.io.PrintWriter getLogWriter() { return null; }
    @Override public void setLogWriter(java.io.PrintWriter o) {}
    @Override public void setLoginTimeout(int s) {}
    @Override public int getLoginTimeout() { return 0; }
    @Override public java.util.logging.Logger getParentLogger() { return null; }
    @Override public <T> T unwrap(Class<T> iface) { throw new UnsupportedOperationException(); }
    @Override public boolean isWrapperFor(Class<?> iface) { return false; }
  }
}
