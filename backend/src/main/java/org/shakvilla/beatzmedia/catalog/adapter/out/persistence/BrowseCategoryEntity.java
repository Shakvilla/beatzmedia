package org.shakvilla.beatzmedia.catalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA entity for the {@code browse_category} table (V304). WU-CAT-2. */
@Entity
@Table(name = "browse_category")
public class BrowseCategoryEntity {

  @Id
  public String id;

  @Column(nullable = false)
  public String title;

  @Column(name = "color_class", nullable = false)
  public String colorClass;
}
