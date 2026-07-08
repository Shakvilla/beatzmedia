package org.shakvilla.beatzmedia.store.adapter.out.persistence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.store.application.port.in.ListStore.StoreQuery;
import org.shakvilla.beatzmedia.store.application.port.out.StoreRepository;
import org.shakvilla.beatzmedia.store.domain.StoreItem;
import org.shakvilla.beatzmedia.store.domain.StoreItemId;
import org.shakvilla.beatzmedia.store.domain.StoreSort;

/**
 * JPA implementation of {@link StoreRepository}. Reads only {@code store_item} /
 * {@code license_option} / {@code merch_variant}; no cross-module joins ({@code artist_id} is a
 * bare reference column). Transaction boundary = the application service. Store ADD §5.2 / §7.
 */
@ApplicationScoped
public class JpaStoreRepository implements StoreRepository {

  private final EntityManager em;
  private final StoreEntityMapper mapper;

  @Inject
  public JpaStoreRepository(EntityManager em, StoreEntityMapper mapper) {
    this.em = em;
    this.mapper = mapper;
  }

  @Override
  public Page<StoreItem> find(StoreQuery query, StoreSort sort, PageRequest page) {
    StringBuilder whereJpql = new StringBuilder("FROM StoreItemEntity s WHERE 1 = 1");
    query.type().ifPresent(t -> whereJpql.append(" AND s.type = :type"));
    query.genre().ifPresent(g -> whereJpql.append(" AND s.genre = :genre"));

    TypedQuery<Long> countQuery = em.createQuery("SELECT COUNT(s) " + whereJpql, Long.class);
    TypedQuery<StoreItemEntity> listQuery =
        em.createQuery("SELECT s " + whereJpql + orderBy(sort), StoreItemEntity.class);

    query.type().ifPresent(t -> {
      countQuery.setParameter("type", t.name());
      listQuery.setParameter("type", t.name());
    });
    query.genre().ifPresent(g -> {
      countQuery.setParameter("genre", g.wireValue());
      listQuery.setParameter("genre", g.wireValue());
    });

    long total = countQuery.getSingleResult();
    List<StoreItemEntity> entities =
        listQuery.setFirstResult(page.offset()).setMaxResults(page.size()).getResultList();

    List<String> ids = entities.stream().map(e -> e.id).toList();
    Map<String, List<LicenseOptionEntity>> licensesByItem = loadLicenseOptionsByItemIds(ids);
    Map<String, List<MerchVariantEntity>> variantsByItem = loadMerchVariantsByItemIds(ids);

    List<StoreItem> items =
        entities.stream()
            .map(
                e ->
                    mapper.toDomain(
                        e,
                        licensesByItem.getOrDefault(e.id, List.of()),
                        variantsByItem.getOrDefault(e.id, List.of())))
            .toList();

    return Page.of(items, page.page(), page.size(), total);
  }

  @Override
  public Optional<StoreItem> findById(StoreItemId id) {
    StoreItemEntity e = em.find(StoreItemEntity.class, id.value());
    if (e == null) {
      return Optional.empty();
    }
    List<LicenseOptionEntity> licenses = loadLicenseOptions(id.value());
    List<MerchVariantEntity> variants = loadMerchVariants(id.value());
    return Optional.of(mapper.toDomain(e, licenses, variants));
  }

  @Override
  public void decrementStock(StoreItemId id, int qty) {
    if (qty <= 0) {
      return;
    }
    // Atomic, floor-guarded decrement (INV-STORE-C): a row with insufficient/absent stock tracking
    // simply does not match the WHERE clause, so the update is a silent no-op — never negative.
    em.createQuery(
            "UPDATE StoreItemEntity s SET s.stockRemaining = s.stockRemaining - :qty"
                + " WHERE s.id = :id AND s.stockRemaining IS NOT NULL AND s.stockRemaining >= :qty")
        .setParameter("qty", qty)
        .setParameter("id", id.value())
        .executeUpdate();
  }

  private static String orderBy(StoreSort sort) {
    return switch (sort) {
      case POPULAR -> " ORDER BY s.popularity DESC NULLS LAST, s.id ASC";
      case NEWEST -> " ORDER BY s.createdAt DESC, s.id ASC";
      case PRICE_ASC -> " ORDER BY s.priceMinor ASC, s.id ASC";
      case PRICE_DESC -> " ORDER BY s.priceMinor DESC, s.id ASC";
    };
  }

  private List<LicenseOptionEntity> loadLicenseOptions(String itemId) {
    return em.createQuery(
            "SELECT l FROM LicenseOptionEntity l WHERE l.storeItemId = :itemId ORDER BY l.sortOrder ASC",
            LicenseOptionEntity.class)
        .setParameter("itemId", itemId)
        .getResultList();
  }

  private List<MerchVariantEntity> loadMerchVariants(String itemId) {
    return em.createQuery(
            "SELECT v FROM MerchVariantEntity v WHERE v.storeItemId = :itemId ORDER BY v.sortOrder ASC",
            MerchVariantEntity.class)
        .setParameter("itemId", itemId)
        .getResultList();
  }

  private Map<String, List<LicenseOptionEntity>> loadLicenseOptionsByItemIds(List<String> itemIds) {
    if (itemIds.isEmpty()) {
      return Map.of();
    }
    List<LicenseOptionEntity> all =
        em.createQuery(
                "SELECT l FROM LicenseOptionEntity l WHERE l.storeItemId IN :ids ORDER BY l.sortOrder ASC",
                LicenseOptionEntity.class)
            .setParameter("ids", itemIds)
            .getResultList();
    Map<String, List<LicenseOptionEntity>> byItem = new LinkedHashMap<>();
    for (LicenseOptionEntity l : all) {
      byItem.computeIfAbsent(l.storeItemId, k -> new java.util.ArrayList<>()).add(l);
    }
    return byItem;
  }

  private Map<String, List<MerchVariantEntity>> loadMerchVariantsByItemIds(List<String> itemIds) {
    if (itemIds.isEmpty()) {
      return Map.of();
    }
    List<MerchVariantEntity> all =
        em.createQuery(
                "SELECT v FROM MerchVariantEntity v WHERE v.storeItemId IN :ids ORDER BY v.sortOrder ASC",
                MerchVariantEntity.class)
            .setParameter("ids", itemIds)
            .getResultList();
    Map<String, List<MerchVariantEntity>> byItem = new LinkedHashMap<>();
    for (MerchVariantEntity v : all) {
      byItem.computeIfAbsent(v.storeItemId, k -> new java.util.ArrayList<>()).add(v);
    }
    return byItem;
  }
}
