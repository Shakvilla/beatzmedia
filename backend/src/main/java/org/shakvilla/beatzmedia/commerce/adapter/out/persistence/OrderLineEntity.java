package org.shakvilla.beatzmedia.commerce.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code order_line} table — the immutable price snapshot. Domain types carry no
 * ORM annotations. Commerce ADD §5.2 / migration V944.
 */
@Entity
@Table(name = "order_line")
public class OrderLineEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", nullable = false)
  public OrderEntity order;

  @Column(name = "kind", nullable = false)
  public String kind;

  @Column(name = "ref_id", nullable = false)
  public String refId;

  @Column(name = "title", nullable = false)
  public String title;

  @Column(name = "unit_price_minor", nullable = false)
  public long unitPriceMinor;

  @Column(name = "currency", nullable = false)
  public String currency;

  @Column(name = "qty", nullable = false)
  public int qty;
}
