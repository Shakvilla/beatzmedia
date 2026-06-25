package org.shakvilla.beatzmedia.platform.application.port.in;

/**
 * SPI that owning modules implement to register a scheduled job with the platform
 * {@link org.shakvilla.beatzmedia.platform.adapter.out.scheduler.SchedulerRegistry}.
 *
 * <p>Design:
 * <ul>
 *   <li>{@link #jobName()} — stable, machine-readable name used as a Micrometer tag and advisory-lock
 *       key. Must be unique across all beans. Convention: {@code <module>.<action>}, e.g.
 *       {@code catalog.go-live}, {@code payments.payout-window}.
 *   <li>{@link #runOnce()} — contains the full idempotent job logic. The registry calls this method
 *       inside an advisory-lock guard so at most one instance runs a given tick in a multi-instance
 *       deployment. Implementations must be idempotent: calling this multiple times must produce the
 *       same result as calling it once (e.g. conditional SQL {@code WHERE status='scheduled'} or
 *       upsert-by-key).
 * </ul>
 *
 * <p>Usage — a catalog module bean example (deferred to WU-CAT-3):
 * <pre>{@code
 * @ApplicationScoped
 * public class GoLiveJob implements ScheduledJob {
 *   @Override public String jobName() { return "catalog.go-live"; }
 *   @Override public void runOnce() {
 *     // UPDATE release SET status='published' WHERE status='scheduled' AND go_live_at<=now()
 *   }
 * }
 * }</pre>
 *
 * <p>This interface lives in the <em>application</em> layer (no framework imports on the interface
 * itself) so domain code may implement it if needed without importing Jakarta/Quarkus types.
 * Implementations are CDI beans ({@code @ApplicationScoped}) discovered by the registry at startup.
 *
 * <p>LLFR-PLATFORM-01.2 / ADD §5.2.
 */
public interface ScheduledJob {

  /**
   * Stable, unique job name used for Micrometer metrics tags and Postgres advisory-lock key
   * derivation. Must not change once shipped (changing the name changes the lock key).
   * Convention: {@code <module>.<action>} in lower-kebab-case.
   *
   * @return never {@code null} or blank
   */
  String jobName();

  /**
   * Execute one idempotent unit of work for this job. Called by the registry at each scheduled
   * tick, inside an advisory-lock guard. Implementations must tolerate being called concurrently
   * on different instances (the lock prevents this in practice, but the implementation must not
   * rely on single-threaded guarantees beyond the advisory lock scope).
   */
  void runOnce();
}
