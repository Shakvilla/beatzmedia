package org.shakvilla.beatzmedia.platform.adapter.out.scheduler;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.platform.adapter.out.scheduler.AdvisoryLockService.LockHandle;
import org.shakvilla.beatzmedia.platform.application.port.in.ScheduledJob;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;

/**
 * Platform scheduler registry — the single point where {@code quarkus-scheduler} ticks are declared
 * and fanned out to {@link ScheduledJob} implementations registered by owning modules.
 *
 * <h2>Design invariants (ADD §5.2 / LLFR-PLATFORM-01.2)</h2>
 * <ul>
 *   <li><strong>No business logic here.</strong> The registry is pure infrastructure: discover jobs,
 *       acquire the advisory lock, delegate, record metrics, release.
 *   <li><strong>Exactly-once per tick — two layers of protection:</strong>
 *       <ol>
 *         <li>{@code concurrentExecution = SKIP} prevents a second tick from running on the
 *             <em>same JVM</em> before the previous one finishes.
 *         <li>A Postgres session-level advisory lock ({@link AdvisoryLockService}) prevents double
 *             execution across <em>different JVM instances</em> (multi-node). The connection is held
 *             open for the full duration of the lock so the session persists.
 *       </ol>
 *   <li><strong>Observable.</strong> Every job tick is timed ({@code scheduler.job.duration}) and
 *       counted ({@code scheduler.job.run}, {@code scheduler.job.skip}, {@code scheduler.job.error})
 *       with a {@code job} tag set to the job's stable {@link ScheduledJob#jobName()}.
 *   <li><strong>Idempotency is the job's responsibility.</strong> The registry guarantees at-most-one
 *       execution per tick; each {@link ScheduledJob} implementation must be safe to re-run
 *       (conditional state transitions, upsert-by-key, etc.).
 * </ul>
 *
 * <h2>Cadences (from ADD §5.2)</h2>
 * <pre>
 *   go-live          every 60 s   — catalog go-live (INV-7)
 *   episode-go-live  every 60 s   — studio podcast episode go-live (INV-7, WU-STU-2)
 *   payout-window    Fri 09:00    — weekly payout reminder/run
 *   payment-recon    every 30 s   — payment reconciliation / timeout poll
 *   digest           daily 08:00  — digest emails
 *   delivery-retry-sweep every 60 s — notifications email/SMS delivery_attempt retry/backoff sweep
 *   analytics-rollup every 5 min  — analytics rollups
 *   search-reindex   every 10 min — search re-index
 * </pre>
 *
 * <h2>Observability labels</h2>
 * <ul>
 *   <li>{@code scheduler.job.duration} (Timer) — wall time of a successful {@code runOnce()} call
 *   <li>{@code scheduler.job.run} (Counter) — incremented after each successful run
 *   <li>{@code scheduler.job.skip} (Counter) — incremented when the advisory lock is not acquired
 *   <li>{@code scheduler.job.error} (Counter) — incremented when {@code runOnce()} throws
 * </ul>
 * All metrics carry a {@code job} tag set to {@link ScheduledJob#jobName()}.
 *
 * <p>Each {@code @Scheduled} method follows the same three-step pattern:
 * {@code acquireLock → fanOut → releaseLock}, delegating to
 * {@link #runWithLock(String)} which finds the matching job by name and executes it.
 */
@ApplicationScoped
public class SchedulerRegistry {

  private static final Logger LOG = Logger.getLogger(SchedulerRegistry.class);

  /** Job names (convention: module.action) — must match ScheduledJob#jobName() implementations. */
  private static final String JOB_GO_LIVE          = "catalog.go-live";
  private static final String JOB_EPISODE_GO_LIVE  = "studio.episode-go-live";
  private static final String JOB_PAYOUT_WINDOW    = "payments.payout-window";
  private static final String JOB_PAYMENT_RECON    = "payments.payment-recon";
  private static final String JOB_DIGEST           = "notifications.digest";
  private static final String JOB_DELIVERY_RETRY   = "notifications.delivery-retry-sweep";
  private static final String JOB_ANALYTICS_ROLLUP = "analytics.rollup";
  private static final String JOB_SEARCH_REINDEX   = "search.reindex";

  @Inject
  Instance<ScheduledJob> jobBeans;

  @Inject
  AdvisoryLockService lockService;

  @Inject
  MeterRegistry meterRegistry;

  /** Lazily built name → job index (rebuilt on first call after start). */
  private volatile Map<String, ScheduledJob> jobIndex;

  // -------------------------------------------------------------------------
  // @Scheduled declarations — ADD §5.2
  // -------------------------------------------------------------------------

  /** Release go-live — INV-7. Every 60 s with single-JVM overlap prevention. */
  @Scheduled(every = "60s", identity = "go-live", concurrentExecution = ConcurrentExecution.SKIP)
  void publishDue() {
    runWithLock(JOB_GO_LIVE);
  }

  /** Studio podcast episode go-live — INV-7 (WU-STU-2). Every 60 s, same cadence as catalog's
   * go-live but a distinct job/lock so the two sweeps never contend for the same advisory lock. */
  @Scheduled(every = "60s", identity = "episode-go-live",
      concurrentExecution = ConcurrentExecution.SKIP)
  void publishDueEpisodes() {
    runWithLock(JOB_EPISODE_GO_LIVE);
  }

  /** Weekly payout reminder / run window — Friday 09:00 UTC. */
  @Scheduled(cron = "0 0 9 ? * FRI", identity = "payout-window",
      concurrentExecution = ConcurrentExecution.SKIP)
  void weeklyPayoutWindow() {
    runWithLock(JOB_PAYOUT_WINDOW);
  }

  /** Payment reconciliation / timeout poll — every 30 s. */
  @Scheduled(every = "30s", identity = "payment-recon",
      concurrentExecution = ConcurrentExecution.SKIP)
  void reconcileAndTimeout() {
    runWithLock(JOB_PAYMENT_RECON);
  }

  /** Digest emails — daily at 08:00 UTC. */
  @Scheduled(cron = "0 0 8 * * ?", identity = "digest",
      concurrentExecution = ConcurrentExecution.SKIP)
  void sendDigests() {
    runWithLock(JOB_DIGEST);
  }

  /**
   * Notifications delivery retry sweep (WU-NOT-2, LLFR-NOTIF-02.1) — every 60 s. Scans due {@code
   * delivery_attempt} rows (pending/failed with {@code next_attempt_at <= now}) and re-sends.
   */
  @Scheduled(every = "60s", identity = "delivery-retry-sweep",
      concurrentExecution = ConcurrentExecution.SKIP)
  void sweepDeliveryRetries() {
    runWithLock(JOB_DELIVERY_RETRY);
  }

  /** Analytics rollups — every 5 minutes. */
  @Scheduled(every = "5m", identity = "analytics-rollup",
      concurrentExecution = ConcurrentExecution.SKIP)
  void runRollups() {
    runWithLock(JOB_ANALYTICS_ROLLUP);
  }

  /** Search re-index — every 10 minutes. */
  @Scheduled(every = "10m", identity = "search-reindex",
      concurrentExecution = ConcurrentExecution.SKIP)
  void reindexSearch() {
    runWithLock(JOB_SEARCH_REINDEX);
  }

  // -------------------------------------------------------------------------
  // Core dispatch — advisory lock + fan-out + metrics
  // -------------------------------------------------------------------------

  /**
   * Locate the {@link ScheduledJob} with the given name (if any), acquire the Postgres advisory
   * lock for this tick, execute {@link ScheduledJob#runOnce()}, then release the lock.
   *
   * <p>If no bean is registered for the name, the tick is a no-op (logged at DEBUG level). This
   * allows the infra to boot before the owning modules implement their jobs — the scheduler ticks
   * harmlessly until the job bean appears.
   *
   * @param jobName stable job name; used as advisory-lock key and Micrometer tag
   */
  void runWithLock(String jobName) {
    ScheduledJob job = index().get(jobName);
    if (job == null) {
      LOG.debugf("No ScheduledJob bean registered for '%s' — tick is a no-op", jobName);
      return;
    }

    long lockKey = AdvisoryLockService.lockKeyFor(jobName);
    Optional<LockHandle> handle = lockService.tryAcquire(lockKey);
    if (handle.isEmpty()) {
      skipCounter(jobName).increment();
      LOG.debugf("Advisory lock not acquired for '%s' — another instance is running this tick",
          jobName);
      return;
    }

    Timer.Sample sample = Timer.start(meterRegistry);
    try (LockHandle lh = handle.get()) {
      LOG.infof("Scheduler: starting job '%s'", jobName);
      job.runOnce();
      runCounter(jobName).increment();
      LOG.infof("Scheduler: completed job '%s'", jobName);
    } catch (Exception ex) {
      errorCounter(jobName).increment();
      LOG.errorf(ex, "Scheduler: job '%s' threw an exception", jobName);
    } finally {
      sample.stop(jobTimer(jobName));
    }
  }

  // -------------------------------------------------------------------------
  // Job discovery
  // -------------------------------------------------------------------------

  /** Returns (and lazily builds) the name → job index. Thread-safe via volatile + benign race. */
  Map<String, ScheduledJob> index() {
    if (jobIndex != null) {
      return jobIndex;
    }
    Map<String, ScheduledJob> map = new ConcurrentHashMap<>();
    for (ScheduledJob job : jobBeans) {
      String name = job.jobName();
      ScheduledJob prev = map.put(name, job);
      if (prev != null) {
        LOG.warnf("Duplicate ScheduledJob name '%s': %s replaced by %s",
            name, prev.getClass().getName(), job.getClass().getName());
      }
      LOG.infof("Scheduler: registered job '%s' → %s", name, job.getClass().getSimpleName());
    }
    jobIndex = map;
    return map;
  }

  /**
   * Returns the current list of registered job names (snapshot). Exposed for health-check and
   * integration tests; not part of the public SPI.
   */
  List<String> registeredJobNames() {
    return List.copyOf(index().keySet());
  }

  // -------------------------------------------------------------------------
  // Micrometer helpers
  // -------------------------------------------------------------------------

  private Timer jobTimer(String jobName) {
    return Timer.builder("scheduler.job.duration")
        .tag("job", jobName)
        .description("Wall-clock duration of a scheduled job tick")
        .register(meterRegistry);
  }

  private Counter runCounter(String jobName) {
    return Counter.builder("scheduler.job.run")
        .tag("job", jobName)
        .description("Number of successful scheduled job executions")
        .register(meterRegistry);
  }

  private Counter skipCounter(String jobName) {
    return Counter.builder("scheduler.job.skip")
        .tag("job", jobName)
        .description("Number of ticks skipped because the advisory lock was not acquired")
        .register(meterRegistry);
  }

  private Counter errorCounter(String jobName) {
    return Counter.builder("scheduler.job.error")
        .tag("job", jobName)
        .description("Number of scheduled job executions that ended in an exception")
        .register(meterRegistry);
  }
}
