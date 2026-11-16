package org.shakvilla.beatzmedia.commerce.adapter.out.persistence;

import java.time.Instant;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.shakvilla.beatzmedia.commerce.application.port.out.DiscoveryPurchaseMetricsRepository;

@ApplicationScoped
public class JpaDiscoveryPurchaseMetricsRepository implements DiscoveryPurchaseMetricsRepository {

  private final EntityManager em;

  @Inject
  public JpaDiscoveryPurchaseMetricsRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public long count(
      String contentKind, String contentId, List<String> memberIds, Instant since, Instant asOf) {
    StringBuilder target = new StringBuilder();
    switch (contentKind) {
      case "single" -> target.append("(l.kind = 'track' and l.ref_id = :contentId)");
      case "album", "ep" -> {
        target.append("(l.kind in ('album', 'album-rest') and l.ref_id = :contentId)");
        appendMembers(target, "track", memberIds);
      }
      case "podcast" -> {
        target.append("(l.kind = 'season-pass' and l.ref_id = :contentId)");
        appendMembers(target, "episode", memberIds);
      }
      default -> throw new IllegalArgumentException("Unsupported discovery kind");
    }
    Query query = em.createNativeQuery("select count(distinct p.order_id) from order_grant_posting p"
        + " join \"order\" o on o.id = p.order_id"
        + " join order_line l on l.order_id = p.order_id"
        + " where p.posted_at >= :since and p.posted_at < :asOf"
        + " and o.status in ('paid', 'fulfilled') and (" + target + ")")
        .setParameter("contentId", contentId)
        .setParameter("since", since)
        .setParameter("asOf", asOf);
    for (int i = 0; i < memberIds.size(); i++) {
      query.setParameter("member" + i, memberIds.get(i));
    }
    return ((Number) query.getSingleResult()).longValue();
  }

  private static void appendMembers(StringBuilder target, String kind, List<String> memberIds) {
    if (memberIds.isEmpty()) return;
    target.append(" or (l.kind = '").append(kind).append("' and l.ref_id in (");
    for (int i = 0; i < memberIds.size(); i++) {
      if (i > 0) target.append(", ");
      target.append(":member").append(i);
    }
    target.append("))");
  }
}
