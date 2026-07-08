package org.shakvilla.beatzmedia.store.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.notNullValue;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Contract conformance test: validates {@code StoreItemDto} / {@code LicenseOption} / {@code
 * MerchVariant} / the uniform error envelope against {@code API-CONTRACT.md} §7 and {@code
 * Frontend/src/types/index.ts} ({@code StoreItem}, {@code LicenseOption}, {@code MerchVariant}).
 * Store ADD §6 / §11.
 */
@QuarkusTest
@Tag("integration")
class StoreContractTest {

  @Inject EntityManager em;

  private String beatId;
  private String merchId;

  @BeforeEach
  @Transactional
  void seed() {
    long n = System.nanoTime();
    beatId = "it-c-beat-" + n;
    merchId = "it-c-merch-" + n;

    em.createNativeQuery(
            "INSERT INTO store_item (id, type, title, artist_name, artist_id, image, price_minor,"
                + " currency, genre, badges, description, popularity, created_at) VALUES (:id,"
                + " 'BEAT_LICENSE', 'Contract Beat', 'Contract Producer', 'artist-c', 'img.png',"
                + " 5000, 'GHS', 'Drill', '[\"STEMS INCLUDED\"]'::jsonb, 'desc', 77, now())"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", beatId)
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO license_option (id, store_item_id, tier, label, price_minor, features,"
                + " terms, sort_order) VALUES (:id, :itemId, 'LEASE', 'Basic Lease', 5000,"
                + " '[\"Tagged MP3\"]'::jsonb, 'MP3 only', 0) ON CONFLICT (store_item_id, tier)"
                + " DO NOTHING")
        .setParameter("id", beatId + "-lease")
        .setParameter("itemId", beatId)
        .executeUpdate();

    em.createNativeQuery(
            "INSERT INTO store_item (id, type, title, artist_name, image, price_minor, currency,"
                + " badges, popularity, created_at, stock_remaining) VALUES (:id, 'MERCH',"
                + " 'Contract Tee', 'Contract Artist', 'img.png', 12000, 'GHS', '[]'::jsonb, 60,"
                + " now(), 5) ON CONFLICT (id) DO NOTHING")
        .setParameter("id", merchId)
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO merch_variant (id, store_item_id, label, options, sort_order) VALUES"
                + " (:id, :itemId, 'Size', '[\"S\",\"M\",\"L\"]'::jsonb, 0) ON CONFLICT (id) DO"
                + " NOTHING")
        .setParameter("id", merchId + "-size")
        .setParameter("itemId", merchId)
        .executeUpdate();
  }

  // ---- Page<StoreItemDto> ------------------------------------------------------------------------

  @Test
  void listStore_matchesPageEnvelope() {
    given()
        .when()
        .get("/v1/store")
        .then()
        .statusCode(200)
        .body("items", notNullValue())
        .body("page", isA(Integer.class))
        .body("size", isA(Integer.class))
        .body("total", isA(Integer.class));
  }

  // ---- StoreItemDto — BEAT_LICENSE + LicenseOption shape --------------------------------------

  @Test
  void getStoreItem_beatLicense_matchesStoreItemDtoShape() {
    given()
        .when()
        .get("/v1/store/" + beatId)
        .then()
        .statusCode(200)
        .body("id", equalTo(beatId))
        .body("type", equalTo("BEAT_LICENSE"))
        .body("title", isA(String.class))
        .body("artistName", equalTo("Contract Producer"))
        .body("artistId", equalTo("artist-c"))
        .body("image", isA(String.class))
        .body("price.amount", isA(Float.class))
        .body("price.currency", equalTo("GHS"))
        .body("genre", equalTo("Drill"))
        .body("badges", isA(java.util.List.class))
        .body("description", equalTo("desc"))
        .body("popularity", equalTo(77))
        .body("createdAt", isA(String.class))
        .body("licenseOptions[0].tier", equalTo("LEASE"))
        .body("licenseOptions[0].label", equalTo("Basic Lease"))
        .body("licenseOptions[0].price.amount", isA(Float.class))
        .body("licenseOptions[0].price.currency", equalTo("GHS"))
        .body("licenseOptions[0].features", isA(java.util.List.class))
        .body("licenseOptions[0].terms", equalTo("MP3 only"));
  }

  // ---- StoreItemDto — MERCH + MerchVariant shape -----------------------------------------------

  @Test
  void getStoreItem_merch_matchesMerchVariantShape() {
    given()
        .when()
        .get("/v1/store/" + merchId)
        .then()
        .statusCode(200)
        .body("type", equalTo("MERCH"))
        .body("variants[0].label", equalTo("Size"))
        .body("variants[0].options", isA(java.util.List.class))
        .body("stockRemaining", equalTo(5));
  }

  // ---- Uniform error envelope --------------------------------------------------------------------

  @Test
  void getStoreItemUnknown_returnsUniformErrorEnvelope() {
    given()
        .when()
        .get("/v1/store/no-such-item-" + System.nanoTime())
        .then()
        .statusCode(404)
        .body("error.code", equalTo("NOT_FOUND"))
        .body("error.message", notNullValue());
  }
}
