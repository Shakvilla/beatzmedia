package org.shakvilla.beatzmedia.commerce.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code cart_item} table. Domain types carry no ORM annotations. Commerce ADD
 * §5.2 / migration V943.
 */
@Entity
@Table(name = "cart_item")
public class CartItemEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "cart_id", nullable = false)
  public CartEntity cart;

  /** Stable line id, e.g. {@code track:last-last}. */
  @Column(name = "line_id", nullable = false)
  public String lineId;

  @Column(name = "kind", nullable = false)
  public String kind;

  @Column(name = "ref_id", nullable = false)
  public String refId;

  @Column(name = "title", nullable = false)
  public String title;

  @Column(name = "subtitle")
  public String subtitle;

  @Column(name = "image")
  public String image;

  @Column(name = "unit_price_minor", nullable = false)
  public long unitPriceMinor;

  @Column(name = "currency", nullable = false)
  public String currency;

  @Column(name = "qty", nullable = false)
  public int qty;

  @Column(name = "stackable", nullable = false)
  public boolean stackable;

  @Column(name = "metadata", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  public String metadataJson;
}
