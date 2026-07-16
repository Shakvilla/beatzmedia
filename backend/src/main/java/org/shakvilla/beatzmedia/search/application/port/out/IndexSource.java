package org.shakvilla.beatzmedia.search.application.port.out;

import java.util.List;

import org.shakvilla.beatzmedia.search.domain.EntityType;
import org.shakvilla.beatzmedia.search.domain.IndexDocument;

/**
 * SPI supplying source entities for a reindex. Each owning module (catalog, store, podcasts, …)
 * contributes one implementation per {@link EntityType} it owns, mapping its own domain objects to
 * {@link IndexDocument}s. The search module never reads another module's tables or ports; sources are
 * discovered via CDI, which keeps the dependency edge pointing {@code module -> search} — the same
 * direction {@code store.adapter.out.persistence.SearchIndexPg} already establishes. Search ADD §9.
 *
 * <p>Implementations must return every entity they own, including ones that are not publicly
 * visible, with {@link IndexDocument#visible()} set accordingly. Reindex is upsert-only, so omitting
 * a now-hidden entity would strand its previous {@code visible=true} document in the index forever.
 */
public interface IndexSource {

  /** The entity type this source supplies. */
  EntityType entityType();

  /** All documents of {@link #entityType()} currently in the owning module. */
  List<IndexDocument> load();
}
