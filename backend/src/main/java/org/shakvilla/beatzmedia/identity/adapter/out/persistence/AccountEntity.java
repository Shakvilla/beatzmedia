package org.shakvilla.beatzmedia.identity.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code account} table. Domain types carry no ORM annotations; this adapter
 * class is the only place Hibernate annotations appear. Identity ADD §5.2 / conventions §6.
 */
@Entity
@Table(name = "account")
public class AccountEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "name", nullable = false)
  public String name;

  @Column(name = "email", nullable = false, unique = true)
  public String email;

  @Column(name = "avatar")
  public String avatar;

  @Column(name = "is_artist", nullable = false)
  public boolean isArtist;

  @Column(name = "is_admin", nullable = false)
  public boolean isAdmin;

  @Column(name = "verified", nullable = false)
  public boolean verified;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;
}
