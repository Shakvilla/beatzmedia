package org.shakvilla.beatzmedia.admin.adapter.out.persistence;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.shakvilla.beatzmedia.admin.application.port.out.PushItemRepository;
import org.shakvilla.beatzmedia.admin.domain.PushItem;

/**
 * JPA implementation of {@link PushItemRepository} (admin ADD §5.2 / §7, WU-ADM-4). Reads/writes
 * only this module's V951 {@code push_item} table; no cross-module joins. Transaction boundary =
 * the calling application service.
 */
@ApplicationScoped
public class JpaPushItemRepository implements PushItemRepository {

  private final EntityManager em;

  @Inject
  public JpaPushItemRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public List<PushItem> list() {
    TypedQuery<PushItemEntity> query = em.createQuery(
        "SELECT p FROM PushItemEntity p ORDER BY p.scheduledAt ASC NULLS LAST",
        PushItemEntity.class);
    return query.getResultList().stream().map(JpaPushItemRepository::toDomain).toList();
  }

  @Override
  public PushItem save(PushItem item) {
    PushItemEntity entity = new PushItemEntity();
    entity.id = item.getId();
    entity.day = item.getDay();
    entity.timeLabel = item.getTimeLabel();
    entity.title = item.getTitle();
    entity.audience = item.getAudience();
    entity.scheduledAt = item.getScheduledAt();
    em.persist(entity);
    return item;
  }

  private static PushItem toDomain(PushItemEntity entity) {
    return new PushItem(
        entity.id, entity.day, entity.timeLabel, entity.title, entity.audience, entity.scheduledAt);
  }
}
