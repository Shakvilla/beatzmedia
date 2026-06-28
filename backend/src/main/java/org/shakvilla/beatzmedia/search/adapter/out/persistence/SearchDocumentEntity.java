package org.shakvilla.beatzmedia.search.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA entity for the {@code search_document} table (ADD §5.2).
 * Domain objects carry no ORM annotations; mapping is done in {@link SearchDocumentMapper}.
 */
@Entity
@Table(name = "search_document")
class SearchDocumentEntity extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "entity_type", nullable = false)
  String entityType;

  @Column(name = "entity_id", nullable = false)
  String entityId;

  @Column(name = "title", nullable = false)
  String title;

  @Column(name = "subtitle")
  String subtitle;

  @Column(name = "search_text", nullable = false)
  String searchText;

  // tsv is maintained by DB trigger (INV-SRCH-4); not mapped as writable column
  @Column(name = "popularity", nullable = false)
  long popularity;

  @Column(name = "visible", nullable = false)
  boolean visible;

  @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
  String payload;

  @Column(name = "indexed_at", nullable = false)
  Instant indexedAt;
}
