package org.shakvilla.beatzmedia.platform.adapter.out.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.inject.Instance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.adapter.out.scheduler.AdvisoryLockService.LockHandle;
import org.shakvilla.beatzmedia.platform.application.port.in.ScheduledJob;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Pure unit tests for {@link SchedulerRegistry} dispatch logic. Uses hand-rolled fakes instead of
 * Quarkus/CDI so there is no container overhead. Testing-strategy §5 / WU-PLT-2.
 *
 * <p>Proves:
 * <ol>
 *   <li>Job is invoked exactly once when the lock is acquired.
 *   <li>Job is skipped (not invoked) when the lock is not acquired.
 *   <li>Lock handle is closed after a successful run.
 *   <li>Lock handle is closed even when the job throws.
 *   <li>Error counter is incremented and exception does not propagate to the caller.
 *   <li>No-op when no bean is registered for the requested job name.
 *   <li>Metrics (run, skip, error counters + timer) are recorded correctly.
 *   <li>Exactly-once under simulated two-node concurrent ticks.
 * </ol>
 */
@Tag("unit")
class SchedulerRegistryUnitTest {

  // -------------------------------------------------------------------------
  // Fakes
  // -------------------------------------------------------------------------

  /**
   * Controllable fake for the advisory lock service. Records whether the handle was acquired and
   * closed; does NOT subclass AdvisoryLockService (no real DataSource needed here).
   */
  static class FakeLockService extends AdvisoryLockService {
    boolean acquireReturns = true;
    final List<Long>  acquired  = new ArrayList<>();
    final List<Long>  closed    = new ArrayList<>();

    FakeLockService() {
      // No real DataSource — we override tryAcquire.
    }

    @Override
    public Optional<LockHandle> tryAcquire(long lockKey) {
      if (acquireReturns) {
        acquired.add(lockKey);
        // Return a fake handle that records close
        FakeLockService self = this;
        return Optional.of(new LockHandle(null, lockKey) {
          @Override
          public void close() {
            self.closed.add(lockKey);
          }
        });
      }
      return Optional.empty();
    }
  }

  /** A ScheduledJob backed by a counter; optionally throws on run. */
  static class CountingJob implements ScheduledJob {
    final String name;
    boolean throwOnRun = false;
    final AtomicInteger runCount = new AtomicInteger();

    CountingJob(String name) {
      this.name = name;
    }

    @Override
    public String jobName() {
      return name;
    }

    @Override
    public void runOnce() {
      if (throwOnRun) {
        throw new RuntimeException("simulated job failure");
      }
      runCount.incrementAndGet();
    }
  }

  /** Minimal Instance<ScheduledJob> fake backed by a list. */
  @SuppressWarnings("all")
  static class FakeInstance implements Instance<ScheduledJob> {
    private final List<ScheduledJob> jobs;

    FakeInstance(List<ScheduledJob> jobs) {
      this.jobs = jobs;
    }

    @Override
    public java.util.Iterator<ScheduledJob> iterator() {
      return jobs.iterator();
    }

    @Override public ScheduledJob get() { throw new UnsupportedOperationException(); }
    @Override public Instance<ScheduledJob> select(java.lang.annotation.Annotation... qualifiers) { throw new UnsupportedOperationException(); }
    @Override public <U extends ScheduledJob> Instance<U> select(Class<U> subtype, java.lang.annotation.Annotation... qualifiers) { throw new UnsupportedOperationException(); }
    @Override public <U extends ScheduledJob> Instance<U> select(jakarta.enterprise.util.TypeLiteral<U> subtype, java.lang.annotation.Annotation... qualifiers) { throw new UnsupportedOperationException(); }
    @Override public boolean isUnsatisfied() { return jobs.isEmpty(); }
    @Override public boolean isAmbiguous() { return false; }
    @Override public void destroy(ScheduledJob instance) {}
    @Override public jakarta.enterprise.inject.Instance.Handle<ScheduledJob> getHandle() { throw new UnsupportedOperationException(); }
    @Override public Iterable<? extends jakarta.enterprise.inject.Instance.Handle<ScheduledJob>> handles() { throw new UnsupportedOperationException(); }
    @Override public boolean isResolvable() { return !jobs.isEmpty(); }
  }

  // -------------------------------------------------------------------------
  // Test setup
  // -------------------------------------------------------------------------

  private FakeLockService lockService;
  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    lockService   = new FakeLockService();
    meterRegistry = new SimpleMeterRegistry();
  }

  private SchedulerRegistry buildRegistry(ScheduledJob... jobs) {
    SchedulerRegistry r = new SchedulerRegistry();
    r.lockService   = lockService;
    r.meterRegistry = meterRegistry;
    r.jobBeans      = new FakeInstance(List.of(jobs));
    return r;
  }

  // -------------------------------------------------------------------------
  // Tests
  // -------------------------------------------------------------------------

  @Test
  void runWithLock_acquiresLock_invokesJob_closesHandle() {
    CountingJob job = new CountingJob("test.job");
    SchedulerRegistry registry = buildRegistry(job);

    registry.runWithLock("test.job");

    assertEquals(1, job.runCount.get(), "Job must be invoked exactly once");
    assertFalse(lockService.acquired.isEmpty(), "Lock must have been acquired");
    assertFalse(lockService.closed.isEmpty(), "Lock handle must have been closed");
    assertEquals(lockService.acquired.get(0), lockService.closed.get(0),
        "Acquired and closed lock keys must match");
  }

  @Test
  void runWithLock_skipsJob_whenLockNotAcquired() {
    CountingJob job = new CountingJob("test.job");
    SchedulerRegistry registry = buildRegistry(job);
    lockService.acquireReturns = false;

    registry.runWithLock("test.job");

    assertEquals(0, job.runCount.get(), "Job must NOT be invoked when lock is not acquired");
    assertTrue(lockService.closed.isEmpty(), "Lock handle must NOT be closed when not acquired");
  }

  @Test
  void runWithLock_closesHandle_evenWhenJobThrows() {
    CountingJob job = new CountingJob("throw.job");
    job.throwOnRun = true;
    SchedulerRegistry registry = buildRegistry(job);

    // Must not propagate the exception
    registry.runWithLock("throw.job");

    assertFalse(lockService.closed.isEmpty(), "Lock handle must be closed even when job throws");
  }

  @Test
  void runWithLock_isNoop_whenNoJobRegistered() {
    SchedulerRegistry registry = buildRegistry();  // empty — no beans

    // Should not throw
    registry.runWithLock("nonexistent.job");

    assertTrue(lockService.acquired.isEmpty(), "No lock acquired for unregistered job");
    assertTrue(lockService.closed.isEmpty(), "No lock closed for unregistered job");
  }

  @Test
  void runWithLock_incrementsRunCounter_onSuccess() {
    CountingJob job = new CountingJob("metrics.job");
    SchedulerRegistry registry = buildRegistry(job);

    registry.runWithLock("metrics.job");

    double runCount = meterRegistry.counter("scheduler.job.run", "job", "metrics.job").count();
    assertEquals(1.0, runCount, "scheduler.job.run counter must be 1 after one successful run");
  }

  @Test
  void runWithLock_incrementsSkipCounter_whenLockNotAcquired() {
    CountingJob job = new CountingJob("skip.job");
    SchedulerRegistry registry = buildRegistry(job);
    lockService.acquireReturns = false;

    registry.runWithLock("skip.job");

    double skipCount = meterRegistry.counter("scheduler.job.skip", "job", "skip.job").count();
    assertEquals(1.0, skipCount, "scheduler.job.skip counter must be 1 when lock is not acquired");
  }

  @Test
  void runWithLock_incrementsErrorCounter_whenJobThrows() {
    CountingJob job = new CountingJob("error.job");
    job.throwOnRun = true;
    SchedulerRegistry registry = buildRegistry(job);

    registry.runWithLock("error.job");

    double errorCount = meterRegistry.counter("scheduler.job.error", "job", "error.job").count();
    assertEquals(1.0, errorCount, "scheduler.job.error counter must be 1 when job throws");
  }

  @Test
  void runWithLock_recordsDuration_onSuccess() {
    CountingJob job = new CountingJob("timed.job");
    SchedulerRegistry registry = buildRegistry(job);

    registry.runWithLock("timed.job");

    long timerCount = meterRegistry.timer("scheduler.job.duration", "job", "timed.job").count();
    assertEquals(1L, timerCount, "scheduler.job.duration timer must record 1 sample after one run");
  }

  @Test
  void index_populatesAllRegisteredJobs() {
    CountingJob a = new CountingJob("module-a.job");
    CountingJob b = new CountingJob("module-b.job");
    SchedulerRegistry registry = buildRegistry(a, b);

    Map<String, ScheduledJob> idx = registry.index();

    assertTrue(idx.containsKey("module-a.job"), "index must contain module-a.job");
    assertTrue(idx.containsKey("module-b.job"), "index must contain module-b.job");
  }

  @Test
  void registeredJobNames_returnsAllNames() {
    CountingJob a = new CountingJob("a.job");
    CountingJob b = new CountingJob("b.job");
    SchedulerRegistry registry = buildRegistry(a, b);

    List<String> names = registry.registeredJobNames();
    assertTrue(names.contains("a.job"));
    assertTrue(names.contains("b.job"));
    assertEquals(2, names.size());
  }

  @Test
  void runWithLock_exactlyOnce_underSimulatedConcurrentTicks() {
    // Simulates two concurrent scheduler ticks: only one gets the lock.
    CountingJob job = new CountingJob("concurrent.job");

    // Registry A wins the lock
    FakeLockService lockA = new FakeLockService();
    lockA.acquireReturns = true;
    SchedulerRegistry registryA = new SchedulerRegistry();
    registryA.lockService   = lockA;
    registryA.meterRegistry = meterRegistry;
    registryA.jobBeans      = new FakeInstance(List.of(job));

    // Registry B loses the lock (simulates another node or concurrent thread)
    FakeLockService lockB = new FakeLockService();
    lockB.acquireReturns = false;
    SchedulerRegistry registryB = new SchedulerRegistry();
    registryB.lockService   = lockB;
    registryB.meterRegistry = meterRegistry;
    registryB.jobBeans      = new FakeInstance(List.of(job));

    registryA.runWithLock("concurrent.job");
    registryB.runWithLock("concurrent.job");

    assertEquals(1, job.runCount.get(),
        "Job must run exactly once when one of two concurrent ticks wins the advisory lock");
  }
}
