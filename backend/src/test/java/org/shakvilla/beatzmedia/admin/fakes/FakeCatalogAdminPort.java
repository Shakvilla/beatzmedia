package org.shakvilla.beatzmedia.admin.fakes;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

import org.shakvilla.beatzmedia.admin.application.port.out.CatalogAdminPort;

/**
 * In-memory fake for {@link CatalogAdminPort}. Records the arguments of the last call to each
 * method so unit tests can assert admin's use cases forward exactly what they received (and,
 * critically, that they do NOT also write an {@code AuditEntry} themselves — the underlying
 * catalog FSM self-audits). Testing-strategy §2.
 */
public class FakeCatalogAdminPort implements CatalogAdminPort {

  public record Call(String actorId, String releaseId, Optional<Instant> goLiveAt, String reason) {}

  private Call lastCall;
  private Supplier<RuntimeException> failure;

  /** Configures the next call to throw the given exception (simulates 404/409 from catalog). */
  public void failNextCallWith(Supplier<RuntimeException> failure) {
    this.failure = failure;
  }

  public Call lastCall() {
    return lastCall;
  }

  @Override
  public void approve(String actorId, String releaseId, Optional<Instant> goLiveAt) {
    maybeFail();
    lastCall = new Call(actorId, releaseId, goLiveAt, null);
  }

  @Override
  public void takedown(String actorId, String releaseId, String reason) {
    maybeFail();
    lastCall = new Call(actorId, releaseId, Optional.empty(), reason);
  }

  @Override
  public void reinstate(String actorId, String releaseId) {
    maybeFail();
    lastCall = new Call(actorId, releaseId, Optional.empty(), null);
  }

  private void maybeFail() {
    if (failure != null) {
      Supplier<RuntimeException> f = failure;
      failure = null;
      throw f.get();
    }
  }
}
