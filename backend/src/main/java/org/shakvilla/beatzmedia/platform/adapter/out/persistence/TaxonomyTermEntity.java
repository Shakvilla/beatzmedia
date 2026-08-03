package org.shakvilla.beatzmedia.platform.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA entity for {@code taxonomy_term} (V972). */
@Entity
@Table(name = "taxonomy_term")
public class TaxonomyTermEntity {

  @Id public String id;

  /** Stored as the snake_case wire value, not the enum name — see {@code TaxonomyKind}. */
  @Column(nullable = false)
  public String kind;

  @Column(nullable = false)
  public String slug;

  @Column(nullable = false)
  public String label;

  @Column(name = "color_class")
  public String colorClass;

  @Column(name = "sort_order", nullable = false)
  public int sortOrder;

  @Column(nullable = false)
  public boolean active;
}
