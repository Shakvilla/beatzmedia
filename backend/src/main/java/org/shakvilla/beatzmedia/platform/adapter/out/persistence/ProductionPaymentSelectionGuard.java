package org.shakvilla.beatzmedia.platform.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;

/** Keeps an untouched migration seed disabled in production until an operator activates a provider. */
@ApplicationScoped
public class ProductionPaymentSelectionGuard {
  private final EntityManager em;

  @Inject
  public ProductionPaymentSelectionGuard(EntityManager em) {
    this.em = em;
  }

  @Transactional
  void onStart(@Observes StartupEvent ignored) {
    if (LaunchMode.current() != LaunchMode.NORMAL) {
      return;
    }
    em.createNativeQuery(
            "UPDATE provider_selection SET provider_code = 'disabled' "
                + "WHERE capability = 'payment' AND updated_by = 'system:migration' AND version = 1")
        .executeUpdate();
  }
}
