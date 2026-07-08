package org.shakvilla.beatzmedia.store.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code license_option} table (BEAT_LICENSE tiers: LEASE/PREMIUM/EXCLUSIVE).
 * Domain types carry no ORM annotations. Store ADD §7 / migration V956.
 */
@Entity
@Table(name = "license_option")
public class LicenseOptionEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "store_item_id", nullable = false)
  public String storeItemId;

  @Column(name = "tier", nullable = false)
  public String tier;

  @Column(name = "label", nullable = false)
  public String label;

  @Column(name = "price_minor", nullable = false)
  public long priceMinor;

  /** JSON array of feature strings. */
  @Column(name = "features", columnDefinition = "jsonb", nullable = false)
  @JdbcTypeCode(SqlTypes.JSON)
  public String featuresJson;

  @Column(name = "terms")
  public String terms;

  @Column(name = "sort_order", nullable = false)
  public short sortOrder;
}
