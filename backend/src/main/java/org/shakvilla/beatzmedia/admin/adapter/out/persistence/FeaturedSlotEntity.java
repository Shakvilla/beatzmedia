package org.shakvilla.beatzmedia.admin.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code featured_slot} table. Domain types carry no ORM annotations; this
 * adapter class is the only place Hibernate annotations appear. Admin ADD §5.2 / §7.
 */
@Entity
@Table(name = "featured_slot")
public class FeaturedSlotEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "position", nullable = false)
  public int position;

  @Column(name = "title", nullable = false)
  public String title;

  @Column(name = "note")
  public String note;

  @Column(name = "is_sponsored", nullable = false)
  public boolean sponsored;
}
