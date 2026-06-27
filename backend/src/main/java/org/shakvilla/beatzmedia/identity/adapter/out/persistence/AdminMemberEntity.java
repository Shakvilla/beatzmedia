package org.shakvilla.beatzmedia.identity.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code admin_member} table. Domain types carry no ORM annotations; this
 * adapter class is the only place Hibernate annotations appear. Identity ADD §5.2 / migration V202.
 */
@Entity
@Table(name = "admin_member")
public class AdminMemberEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "account_id", nullable = false)
  public String accountId;

  @Column(name = "role", nullable = false)
  public String role;

  @Column(name = "last_active_at")
  public Instant lastActiveAt;
}
