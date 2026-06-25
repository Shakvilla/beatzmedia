package org.shakvilla.beatzmedia.platform.adapter.out.scheduler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

/**
 * Thin wrapper around Postgres advisory-lock functions so the {@link SchedulerRegistry} can ensure
 * at most one JVM instance executes a given scheduler tick across a multi-node deployment.
 *
 * <h2>Session-level locks and connection lifetime</h2>
 * Postgres session-level advisory locks ({@code pg_try_advisory_lock} / {@code pg_advisory_unlock})
 * are bound to the lifetime of the DB <em>session (connection)</em>. If the connection is closed
 * after {@code pg_try_advisory_lock} the lock is immediately released. Therefore the implementation
 * holds a <em>single dedicated JDBC connection</em> open for the full duration of the lock — from
 * {@link #tryAcquire(long)} until {@link LockHandle#close()} — and never returns that connection to
 * the pool while the lock is active.
 *
 * <p>This means each scheduler tick that wins the advisory lock holds one extra DB connection beyond
 * the pool's normal request traffic. Ticks are short (sub-second for most jobs), so the extra
 * connection is released quickly.
 *
 * <p>ADD §5.2 — "a DB advisory lock guards multi-instance runs so only one instance executes a
 * given tick."
 *
 * <h2>Usage (by SchedulerRegistry)</h2>
 * <pre>{@code
 * Optional<AdvisoryLockService.LockHandle> handle = lockService.tryAcquire(key);
 * if (handle.isEmpty()) { return; }   // another node holds the lock — skip
 * try (LockHandle lh = handle.get()) {
 *     job.runOnce();
 * }
 * }</pre>
 */
@ApplicationScoped
public class AdvisoryLockService {

  private static final Logger LOG = Logger.getLogger(AdvisoryLockService.class);

  @Inject
  DataSource dataSource;

  /**
   * Derive a stable {@code int8} advisory-lock key from the job name. Uses Java's standard
   * {@link String#hashCode()} mapped into the full {@code long} range by mixing the 32-bit hash
   * into the upper and lower 32 bits. This produces a stable, non-zero key across JVM restarts.
   */
  static long lockKeyFor(String jobName) {
    int h = jobName.hashCode();
    // Mix the 32-bit hash into a 64-bit long — avoids zero and spreads the key space.
    return ((long) h << 32) | (Integer.toUnsignedLong(h));
  }

  /**
   * Attempt to acquire a Postgres session-level advisory lock for the given key. If acquired, a
   * {@link LockHandle} is returned; the caller <em>must</em> close it (in a {@code try-with-resources}
   * or {@code finally} block) to release both the advisory lock and the underlying JDBC connection.
   *
   * @param lockKey the advisory lock key (derive via {@link #lockKeyFor(String)})
   * @return {@link Optional} containing the handle if acquired; empty if another session holds it
   *         or if a DB error occurs (fail-safe: skip rather than double-run)
   */
  public Optional<LockHandle> tryAcquire(long lockKey) {
    Connection conn = null;
    try {
      conn = dataSource.getConnection();
      conn.setAutoCommit(true);  // session-level locks work independently of transactions
      try (PreparedStatement ps = conn.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
        ps.setLong(1, lockKey);
        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next() && rs.getBoolean(1)) {
            // Lock acquired — hand the open connection to the handle so the lock survives
            Connection held = conn;
            conn = null;   // prevent closure in our finally block
            return Optional.of(new LockHandle(held, lockKey));
          }
        }
      }
      LOG.debugf("Advisory lock %d already held by another instance — skipping tick", lockKey);
    } catch (SQLException e) {
      // Log and treat as "lock not acquired" — skip rather than risk double-execution.
      LOG.warnf(e, "Failed to acquire advisory lock %d; skipping tick to be safe", lockKey);
    } finally {
      if (conn != null) {
        try { conn.close(); } catch (SQLException ignored) {}
      }
    }
    return Optional.empty();
  }

  // -------------------------------------------------------------------------
  // Lock handle
  // -------------------------------------------------------------------------

  /**
   * An {@link AutoCloseable} handle that keeps the Postgres session alive (holding the advisory
   * lock) until {@link #close()} is called. Close is idempotent.
   */
  public static class LockHandle implements AutoCloseable {

    private final Connection conn;
    private final long lockKey;
    private volatile boolean closed = false;

    LockHandle(Connection conn, long lockKey) {
      this.conn    = conn;
      this.lockKey = lockKey;
    }

    /**
     * Release the advisory lock and return the JDBC connection to the pool. Safe to call multiple
     * times (idempotent after first call).
     */
    @Override
    public void close() {
      if (closed) {
        return;
      }
      closed = true;
      try {
        try (PreparedStatement ps = conn.prepareStatement("SELECT pg_advisory_unlock(?)")) {
          ps.setLong(1, lockKey);
          ps.executeQuery();
        }
      } catch (SQLException e) {
        LOG.warnf(e,
            "Failed to release advisory lock %d — lock expires when connection closes", lockKey);
      } finally {
        try { conn.close(); } catch (SQLException ignored) {}
      }
    }
  }
}
