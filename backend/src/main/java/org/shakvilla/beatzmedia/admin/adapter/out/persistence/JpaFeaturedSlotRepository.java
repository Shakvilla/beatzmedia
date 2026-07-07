package org.shakvilla.beatzmedia.admin.adapter.out.persistence;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.shakvilla.beatzmedia.admin.application.port.out.FeaturedSlotRepository;
import org.shakvilla.beatzmedia.admin.domain.FeaturedSlot;

/**
 * JPA implementation of {@link FeaturedSlotRepository} (admin ADD §5.2 / §7, WU-ADM-4). Reads/
 * writes only this module's V951 {@code featured_slot} table; no cross-module joins. Transaction
 * boundary = the calling application service.
 */
@ApplicationScoped
public class JpaFeaturedSlotRepository implements FeaturedSlotRepository {

  private final EntityManager em;

  @Inject
  public JpaFeaturedSlotRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public List<FeaturedSlot> listOrdered() {
    TypedQuery<FeaturedSlotEntity> query = em.createQuery(
        "SELECT s FROM FeaturedSlotEntity s ORDER BY s.position ASC", FeaturedSlotEntity.class);
    return query.getResultList().stream().map(JpaFeaturedSlotRepository::toDomain).toList();
  }

  @Override
  public List<FeaturedSlot> replaceAll(List<FeaturedSlot> ordered) {
    // Full-set replace: delete all existing rows, then insert the new ordered set. Both operations
    // run inside the caller's @Transactional boundary.
    em.createQuery("DELETE FROM FeaturedSlotEntity").executeUpdate();
    em.flush();

    for (FeaturedSlot slot : ordered) {
      FeaturedSlotEntity entity = new FeaturedSlotEntity();
      entity.id = slot.getId();
      entity.position = slot.getPosition();
      entity.title = slot.getTitle();
      entity.note = slot.getNote();
      entity.sponsored = slot.isSponsored();
      em.persist(entity);
    }
    em.flush();

    return listOrdered();
  }

  private static FeaturedSlot toDomain(FeaturedSlotEntity entity) {
    return new FeaturedSlot(entity.id, entity.position, entity.title, entity.note, entity.sponsored);
  }
}
