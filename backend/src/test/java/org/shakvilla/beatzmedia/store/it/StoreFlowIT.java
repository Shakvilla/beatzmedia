package org.shakvilla.beatzmedia.store.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * End-to-end integration for WU-STO-1 (LLFR-STORE-01.1 – 01.2). Testcontainers Postgres +
 * REST-assured against the real store repository. Proves filter/sort combinations, detail per
 * type, 404, and the dev-seed fixture. Store ADD §11.
 */
@QuarkusTest
@Tag("integration")
class StoreFlowIT {

  @Inject EntityManager em;

  private String trackId;
  private String beatId;
  private String merchId;
  private String exclusiveId;

  @BeforeEach
  @Transactional
  void seed() {
    long n = System.nanoTime();
    trackId = "it-track-" + n;
    beatId = "it-beat-" + n;
    merchId = "it-merch-" + n;
    exclusiveId = "it-exclusive-" + n;

    em.createNativeQuery(
            "INSERT INTO store_item (id, type, title, artist_name, image, price_minor, currency,"
                + " genre, badges, popularity, created_at, quality) VALUES (:id, 'TRACK', 'IT"
                + " Track', 'IT Artist', 'img.png', 450, 'GHS', 'Drill', '[]'::jsonb, 77, now(),"
                + " 'Lossless') ON CONFLICT (id) DO NOTHING")
        .setParameter("id", trackId)
        .executeUpdate();

    em.createNativeQuery(
            "INSERT INTO store_item (id, type, title, artist_name, image, price_minor, currency,"
                + " genre, badges, popularity, created_at) VALUES (:id, 'BEAT_LICENSE', 'IT Beat',"
                + " 'IT Producer', 'img.png', 5000, 'GHS', 'Drill', '[]'::jsonb, 88, now() -"
                + " interval '1 day') ON CONFLICT (id) DO NOTHING")
        .setParameter("id", beatId)
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO license_option (id, store_item_id, tier, label, price_minor, features,"
                + " sort_order) VALUES (:id1, :itemId, 'LEASE', 'Basic Lease', 5000,"
                + " '[]'::jsonb, 0), (:id2, :itemId, 'PREMIUM', 'Premium Stems', 20000,"
                + " '[]'::jsonb, 1), (:id3, :itemId, 'EXCLUSIVE', 'Exclusive', 100000,"
                + " '[]'::jsonb, 2) ON CONFLICT (store_item_id, tier) DO NOTHING")
        .setParameter("itemId", beatId)
        .setParameter("id1", beatId + "-lease")
        .setParameter("id2", beatId + "-premium")
        .setParameter("id3", beatId + "-exclusive")
        .executeUpdate();

    em.createNativeQuery(
            "INSERT INTO store_item (id, type, title, artist_name, image, price_minor, currency,"
                + " badges, popularity, created_at, stock_remaining) VALUES (:id, 'MERCH', 'IT"
                + " Tee', 'IT Artist', 'img.png', 12000, 'GHS', '[]'::jsonb, 60, now(), 5) ON"
                + " CONFLICT (id) DO NOTHING")
        .setParameter("id", merchId)
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO merch_variant (id, store_item_id, label, options, sort_order) VALUES"
                + " (:id, :itemId, 'Size', '[\"S\",\"M\",\"L\"]'::jsonb, 0) ON CONFLICT (id) DO"
                + " NOTHING")
        .setParameter("id", merchId + "-size")
        .setParameter("itemId", merchId)
        .executeUpdate();

    em.createNativeQuery(
            "INSERT INTO store_item (id, type, title, artist_name, image, price_minor, currency,"
                + " badges, popularity, created_at, drops_at, stock_remaining) VALUES (:id,"
                + " 'EXCLUSIVE', 'IT VIP', 'IT Artist', 'img.png', 80000, 'GHS', '[]'::jsonb, 99,"
                + " now(), now() + interval '10 days', 3) ON CONFLICT (id) DO NOTHING")
        .setParameter("id", exclusiveId)
        .executeUpdate();
  }

  // ---- LLFR-STORE-01.1: browse ---------------------------------------------------------------

  @Test
  void listStore_noFilter_returnsPagedItems() {
    given()
        .when()
        .get("/v1/store")
        .then()
        .statusCode(200)
        .body("items", notNullValue())
        .body("total", greaterThanOrEqualTo(4))
        .body("page", equalTo(1))
        .body("size", equalTo(20));
  }

  @Test
  void listStore_typeFilter_returnsOnlyMatchingItems() {
    given()
        .queryParam("type", "BEAT_LICENSE")
        .when()
        .get("/v1/store")
        .then()
        .statusCode(200)
        .body("items.type", everyItem(equalTo("BEAT_LICENSE")))
        .body("items.id", hasItem(beatId));
  }

  @Test
  void listStore_genreFilter_returnsOnlyMatchingItems() {
    given()
        .queryParam("genre", "Drill")
        .when()
        .get("/v1/store")
        .then()
        .statusCode(200)
        .body("items.genre", everyItem(equalTo("Drill")));
  }

  @Test
  void listStore_invalidType_returns422() {
    given()
        .queryParam("type", "NOT_A_TYPE")
        .when()
        .get("/v1/store")
        .then()
        .statusCode(422)
        .body("error.code", equalTo("VALIDATION"))
        .body("error.field", equalTo("type"));
  }

  @Test
  void listStore_invalidGenre_returns422() {
    given()
        .queryParam("genre", "NotAGenre")
        .when()
        .get("/v1/store")
        .then()
        .statusCode(422)
        .body("error.code", equalTo("VALIDATION"))
        .body("error.field", equalTo("genre"));
  }

  @Test
  void listStore_invalidSort_returns422() {
    given()
        .queryParam("sort", "not-a-sort")
        .when()
        .get("/v1/store")
        .then()
        .statusCode(422)
        .body("error.code", equalTo("VALIDATION"))
        .body("error.field", equalTo("sort"));
  }

  @Test
  void listStore_sortPriceAsc_ordersByAscendingPrice() {
    java.util.List<Float> amounts =
        given()
            .queryParam("sort", "price-asc")
            .queryParam("size", 100)
            .when()
            .get("/v1/store")
            .then()
            .statusCode(200)
            .extract()
            .path("items.price.amount");
    java.util.List<Float> sorted = new java.util.ArrayList<>(amounts);
    java.util.Collections.sort(sorted);
    org.junit.jupiter.api.Assertions.assertEquals(sorted, amounts);
  }

  @Test
  void listStore_pagination_respectsPageAndSize() {
    given()
        .queryParam("page", 1)
        .queryParam("size", 1)
        .when()
        .get("/v1/store")
        .then()
        .statusCode(200)
        .body("items.size()", equalTo(1))
        .body("size", equalTo(1));
  }

  // ---- LLFR-STORE-01.2: detail per type ---------------------------------------------------------

  @Test
  void getStoreItem_track_returnsQuality() {
    given()
        .when()
        .get("/v1/store/" + trackId)
        .then()
        .statusCode(200)
        .body("id", equalTo(trackId))
        .body("type", equalTo("TRACK"))
        .body("quality", equalTo("Lossless"));
  }

  @Test
  void getStoreItem_beatLicense_returnsThreeLicenseTiers() {
    given()
        .when()
        .get("/v1/store/" + beatId)
        .then()
        .statusCode(200)
        .body("type", equalTo("BEAT_LICENSE"))
        .body("licenseOptions.size()", equalTo(3))
        .body("licenseOptions.tier", hasItem("LEASE"))
        .body("licenseOptions.tier", hasItem("PREMIUM"))
        .body("licenseOptions.tier", hasItem("EXCLUSIVE"));
  }

  @Test
  void getStoreItem_merch_returnsVariantsAndStock() {
    given()
        .when()
        .get("/v1/store/" + merchId)
        .then()
        .statusCode(200)
        .body("type", equalTo("MERCH"))
        .body("variants.size()", equalTo(1))
        .body("variants[0].label", equalTo("Size"))
        .body("stockRemaining", equalTo(5));
  }

  @Test
  void getStoreItem_exclusive_returnsDropsAtAndStock() {
    given()
        .when()
        .get("/v1/store/" + exclusiveId)
        .then()
        .statusCode(200)
        .body("type", equalTo("EXCLUSIVE"))
        .body("dropsAt", notNullValue())
        .body("stockRemaining", equalTo(3));
  }

  @Test
  void getStoreItem_unknownId_returns404() {
    given()
        .when()
        .get("/v1/store/does-not-exist-" + System.nanoTime())
        .then()
        .statusCode(404)
        .body("error.code", equalTo("NOT_FOUND"));
  }

  // ---- Dev-seed fixture (Store ADD §7 seed source: Frontend/src/lib/store-data.ts) --------------

  @Test
  void devSeed_beatKonongoDrill_hasThreeLicenseTiersOrderedLeaseToExclusive() {
    given()
        .when()
        .get("/v1/store/beat-konongo-drill")
        .then()
        .statusCode(200)
        .body("licenseOptions.size()", equalTo(3))
        .body("licenseOptions[0].tier", equalTo("LEASE"))
        .body("licenseOptions[1].tier", equalTo("PREMIUM"))
        .body("licenseOptions[2].tier", equalTo("EXCLUSIVE"))
        .body("price.amount", equalTo(50.0f));
  }

  @Test
  void devSeed_merchBsherifTee_hasVariantsAndStock() {
    given()
        .when()
        .get("/v1/store/merch-bsherif-tee")
        .then()
        .statusCode(200)
        .body("variants.size()", equalTo(2))
        .body("stockRemaining", equalTo(42));
  }

  @Test
  void devSeed_exclusiveMeetGreet_hasDropsAtAndStock() {
    given()
        .when()
        .get("/v1/store/exclusive-meet-greet")
        .then()
        .statusCode(200)
        .body("dropsAt", notNullValue())
        .body("stockRemaining", equalTo(12));
  }
}
