package org.shakvilla.beatzmedia.commerce.adapter.out.persistence;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code cart} table. Domain types carry no ORM annotations. Commerce ADD §5.2 /
 * migration V943.
 */
@Entity
@Table(name = "cart")
public class CartEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "account_id", nullable = false, unique = true)
  public String accountId;

  @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
  public List<CartItemEntity> items = new ArrayList<>();
}
