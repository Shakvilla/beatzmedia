package org.shakvilla.beatzmedia.platform.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.platform.application.port.out.ProviderSelectionRepository;
import org.shakvilla.beatzmedia.platform.domain.ProviderCapability;
import org.shakvilla.beatzmedia.platform.domain.ProviderCode;
import org.shakvilla.beatzmedia.platform.domain.ProviderSelection;

/** JPA adapter using a compare-and-set update so concurrent operators cannot overwrite each other. */
@ApplicationScoped
public class JpaProviderSelectionRepository implements ProviderSelectionRepository {
  private final EntityManager em;

  @Inject
  public JpaProviderSelectionRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public List<ProviderSelection> listAll() {
    return em.createQuery("SELECT p FROM ProviderSelectionEntity p", ProviderSelectionEntity.class)
        .getResultList().stream()
        .map(JpaProviderSelectionRepository::toDomain)
        .toList();
  }

  @Override
  public Optional<ProviderSelection> updateIfVersion(
      ProviderCapability capability,
      ProviderCode provider,
      long expectedVersion,
      Instant updatedAt,
      String updatedBy) {
    int changed =
        em.createQuery(
                "UPDATE ProviderSelectionEntity p SET p.providerCode = :provider, "
                    + "p.version = p.version + 1, p.updatedAt = :updatedAt, p.updatedBy = :updatedBy "
                    + "WHERE p.capability = :capability AND p.version = :expectedVersion")
            .setParameter("provider", provider.wireValue())
            .setParameter("updatedAt", updatedAt)
            .setParameter("updatedBy", updatedBy)
            .setParameter("capability", capability.wireValue())
            .setParameter("expectedVersion", expectedVersion)
            .executeUpdate();
    if (changed != 1) {
      return Optional.empty();
    }
    em.clear();
    return Optional.ofNullable(em.find(ProviderSelectionEntity.class, capability.wireValue()))
        .map(JpaProviderSelectionRepository::toDomain);
  }

  private static ProviderSelection toDomain(ProviderSelectionEntity entity) {
    ProviderCapability capability = ProviderCapability.fromWireValue(entity.capability);
    return new ProviderSelection(
        capability,
        ProviderCode.fromWireValue(capability, entity.providerCode),
        entity.version,
        entity.updatedAt,
        entity.updatedBy);
  }
}
