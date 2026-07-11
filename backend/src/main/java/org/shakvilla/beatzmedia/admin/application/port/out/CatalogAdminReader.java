package org.shakvilla.beatzmedia.admin.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.admin.domain.CatalogFilter;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Output port for admin's cross-module CATALOG READS: implemented by a direct-JPA adapter reading
 * catalog's {@code release}/{@code release_track}/{@code track}/{@code split_entry}/{@code
 * artist_profile} tables in-process — the SAME documented, accepted exception to strict hexagonal
 * purity as {@link IdentityReader} (admin ADD §4.3/§13). Mutations go through {@link
 * CatalogAdminPort} instead (a genuine cross-module input-port call, kept separate — mirrors the
 * {@code IdentityReader}/{@code AccountAdminPort} split from WU-ADM-2). Admin ADD §4.3.
 */
public interface CatalogAdminReader {

  /** Paged, filtered list of releases ordered newest-submitted first. */
  Page<CatalogRow> list(CatalogFilter filter, String q, PageRequest page);

  /** Loads one release's full moderation detail (tracklist + splits), or empty if not found. */
  Optional<CatalogDetailRow> detail(String releaseId);

  /** Whole-catalog moderation counts (Category A, independent of any filter). */
  CatalogCounts counts();

  /** Summary line item, backs {@code CatalogItemRowView}. */
  record CatalogRow(
      String id, String title, String artistId, String artistName, String type, int trackCount,
      String status) {}

  /** Full detail, backs {@code CatalogItemDetailView} (minus {@code actionLog}). */
  record CatalogDetailRow(
      String id,
      String title,
      String artistId,
      String artistName,
      String type,
      String status,
      Instant createdAt,
      List<TrackRow> tracks,
      List<SplitRow> splits) {}

  record TrackRow(int position, String trackId, String title, int durationSec, long priceMinor) {}

  record SplitRow(String trackId, String name, String role, int percent, String confirmation) {}

  record CatalogCounts(long pending, long published, long takedown) {}
}
