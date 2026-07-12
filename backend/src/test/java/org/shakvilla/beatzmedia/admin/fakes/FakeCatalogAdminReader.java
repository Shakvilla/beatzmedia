package org.shakvilla.beatzmedia.admin.fakes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.shakvilla.beatzmedia.admin.application.port.out.CatalogAdminReader;
import org.shakvilla.beatzmedia.admin.domain.CatalogFilter;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * In-memory fake for {@link CatalogAdminReader}. Testing-strategy §2.
 */
public class FakeCatalogAdminReader implements CatalogAdminReader {

  private final Map<String, CatalogDetailRow> releases = new java.util.LinkedHashMap<>();

  /** Seeds a release, deriving its summary row from the detail row's fields. */
  public void seed(CatalogDetailRow detail) {
    releases.put(detail.id(), detail);
  }

  @Override
  public Page<CatalogRow> list(CatalogFilter filter, String q, PageRequest page) {
    List<String> statuses = bucketStatuses(filter);
    Stream<CatalogDetailRow> stream = releases.values().stream();
    if (statuses != null) {
      stream = stream.filter(r -> statuses.contains(r.status()));
    }
    if (q != null && !q.isBlank()) {
      String needle = q.toLowerCase(Locale.ROOT);
      stream = stream.filter(r -> r.title().toLowerCase(Locale.ROOT).contains(needle)
          || r.artistName().toLowerCase(Locale.ROOT).contains(needle));
    }
    List<CatalogRow> all = stream
        .map(r -> new CatalogRow(
            r.id(), r.title(), r.artistId(), r.artistName(), r.type(), r.tracks().size(), r.status()))
        .toList();

    long total = all.size();
    int from = Math.min(page.offset(), all.size());
    int to = Math.min(from + page.size(), all.size());
    return Page.of(new ArrayList<>(all.subList(from, to)), page.page(), page.size(), total);
  }

  @Override
  public Optional<CatalogDetailRow> detail(String releaseId) {
    return Optional.ofNullable(releases.get(releaseId));
  }

  @Override
  public CatalogCounts counts() {
    long pending = releases.values().stream()
        .filter(r -> r.status().equals("draft") || r.status().equals("in_review")).count();
    long published = releases.values().stream()
        .filter(r -> r.status().equals("scheduled") || r.status().equals("live")).count();
    long takedown = releases.values().stream().filter(r -> r.status().equals("takedown")).count();
    return new CatalogCounts(pending, published, takedown);
  }

  private static List<String> bucketStatuses(CatalogFilter filter) {
    if (filter == null) {
      return null;
    }
    return switch (filter) {
      case PENDING -> List.of("draft", "in_review");
      case PUBLISHED -> List.of("scheduled", "live");
      case TAKEDOWN -> List.of("takedown");
    };
  }
}
