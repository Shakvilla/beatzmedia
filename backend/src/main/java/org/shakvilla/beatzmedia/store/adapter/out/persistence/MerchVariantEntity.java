package org.shakvilla.beatzmedia.store.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code merch_variant} table (MERCH configurable attributes, e.g. Size).
 * Domain types carry no ORM annotations. Store ADD §7 / migration V957.
 */
@Entity
@Table(name = "merch_variant")
public class MerchVariantEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "store_item_id", nullable = false)
  public String storeItemId;

  @Column(name = "label", nullable = false)
  public String label;

  /** JSON array of selectable option strings, e.g. {@code ["S","M","L","XL"]}. */
  @Column(name = "options", columnDefinition = "jsonb", nullable = false)
  @JdbcTypeCode(SqlTypes.JSON)
  public String optionsJson;

  @Column(name = "sort_order", nullable = false)
  public short sortOrder;
}
