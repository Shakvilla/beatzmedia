package org.shakvilla.beatzmedia.catalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code split_entry} table. Catalog ADD §5.2 / V305 migration.
 */
@Entity
@Table(name = "split_entry")
public class SplitEntryEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "track_id", nullable = false)
  public String trackId;

  @Column(name = "name", nullable = false)
  public String name;

  @Column(name = "email", nullable = false)
  public String email;

  @Column(name = "role", nullable = false)
  public String role;

  @Column(name = "percent", nullable = false)
  public int percent;

  /** Values: self | confirmed | pending | auto */
  @Column(name = "confirmation", nullable = false)
  public String confirmation;
}
