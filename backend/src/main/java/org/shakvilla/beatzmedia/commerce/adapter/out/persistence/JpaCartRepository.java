package org.shakvilla.beatzmedia.commerce.adapter.out.persistence;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.commerce.application.port.out.CartRepository;
import org.shakvilla.beatzmedia.commerce.domain.Cart;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * JPA/Panache implementation of {@link CartRepository}. Reads/writes commerce tables only ({@code
 * cart}, {@code cart_item}); no cross-module joins. Transaction boundary = the application service
 * ({@code @Transactional} on use-case impls). Commerce ADD §5.2.
 */
@ApplicationScoped
public class JpaCartRepository implements CartRepository {

  private final EntityManager em;
  private final CartEntityMapper mapper;

  @Inject
  public JpaCartRepository(EntityManager em, CartEntityMapper mapper) {
    this.em = em;
    this.mapper = mapper;
  }

  @Override
  public Optional<Cart> findByAccount(AccountId account) {
    return em
        .createQuery(
            "SELECT c FROM CartEntity c WHERE c.accountId = :accountId", CartEntity.class)
        .setParameter("accountId", account.value())
        .getResultStream()
        .findFirst()
        .map(mapper::toDomain);
  }

  @Override
  public Cart save(Cart cart) {
    CartEntity existing = em.find(CartEntity.class, cart.getId().value());
    if (existing == null) {
      CartEntity entity = mapper.toEntity(cart, null);
      em.persist(entity);
      em.flush();
      return mapper.toDomain(entity);
    }
    CartEntity updated = mapper.toEntity(cart, existing);
    em.flush();
    return mapper.toDomain(updated);
  }
}
