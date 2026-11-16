package org.shakvilla.beatzmedia.library.adapter.out.persistence;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.library.application.port.in.GetDiscoveryLibraryMetrics.Signals;
import org.shakvilla.beatzmedia.library.application.port.out.DiscoveryLibraryMetricsRepository;

@ApplicationScoped
public class JpaDiscoveryLibraryMetricsRepository implements DiscoveryLibraryMetricsRepository {

  private final EntityManager em;

  @Inject
  public JpaDiscoveryLibraryMetricsRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public Signals get(String contentKind, String contentId, String creatorId, Instant since, Instant asOf) {
    String saveTable = switch (contentKind) {
      case "single" -> "liked_track";
      case "album", "ep" -> "saved_album";
      case "podcast" -> null;
      default -> throw new IllegalArgumentException("Unsupported discovery kind");
    };
    String saveColumn = "single".equals(contentKind) ? "track_id" : "album_id";
    long saves = saveTable == null ? 0 : count(
        "select count(*) from " + saveTable + " where " + saveColumn
            + " = :target and created_at >= :since and created_at < :asOf", contentId, since, asOf);
    String followTable = "podcast".equals(contentKind) ? "followed_show" : "followed_artist";
    String followColumn = "podcast".equals(contentKind) ? "show_id" : "artist_id";
    long follows = count("select count(*) from " + followTable + " where " + followColumn
        + " = :target and created_at >= :since and created_at < :asOf", creatorId, since, asOf);
    return new Signals(saves, follows);
  }

  private long count(String sql, String target, Instant since, Instant asOf) {
    return ((Number) em.createNativeQuery(sql)
        .setParameter("target", target)
        .setParameter("since", since)
        .setParameter("asOf", asOf)
        .getSingleResult()).longValue();
  }
}
