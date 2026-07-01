package org.shakvilla.beatzmedia.identity.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code social_identity} table. Domain types carry no ORM annotations; this
 * adapter class is the only place Hibernate annotations appear. Identity ADD §5.2 / conventions §6.
 */
@Entity
@Table(name = "social_identity")
public class SocialIdentityEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "account_id", nullable = false)
  public String accountId;

  @Column(name = "provider", nullable = false)
  public String provider;

  @Column(name = "provider_uid", nullable = false)
  public String providerUid;
}
