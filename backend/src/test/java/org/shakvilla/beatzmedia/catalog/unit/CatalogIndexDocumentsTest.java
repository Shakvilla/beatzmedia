package org.shakvilla.beatzmedia.catalog.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.adapter.out.search.CatalogIndexDocuments;
import org.shakvilla.beatzmedia.search.domain.EntityType;

class CatalogIndexDocumentsTest {

  /** Mirrors SearchDocumentMapper.ALLOWED_PAYLOAD_KEYS — anything else is silently stripped on write. */
  private static final Set<String> TRACK_ALLOWED =
      Set.of("image", "duration_sec", "price_minor", "price_amount", "price_currency", "quality", "type", "genre");
  private static final Set<String> ARTIST_ALLOWED = Set.of("image", "genre");
  private static final Set<String> ALBUM_ALLOWED = Set.of("image", "price_minor", "price_amount", "price_currency", "genre");
  private static final Set<String> PLAYLIST_ALLOWED = Set.of("image");

  @Test
  void track_maps_title_subtitle_and_searchable_text() {
    var track = CatalogTestFixtures.track("t1", "Second Sermon", "black-sherif", "Black Sherif", 1500L);

    var doc = CatalogIndexDocuments.fromTrack(track, true);

    assertEquals(EntityType.TRACK, doc.entityType());
    assertEquals("t1", doc.entityId());
    assertEquals("Second Sermon", doc.title());
    assertEquals("Black Sherif", doc.subtitle());
    assertTrue(doc.searchText().contains("Second Sermon"), "searchText should carry the title");
    assertTrue(doc.searchText().contains("Black Sherif"), "searchText should carry the artist name");
    assertEquals(1500L, doc.popularity().score());
    assertTrue(doc.visible());
  }

  @Test
  void track_gated_by_a_non_live_release_is_indexed_with_visible_false() {
    // WU-SRCH-2 Finding 1 regression: a taken-down (or otherwise non-live-gated) track must still
    // produce a document — upsert-only reindex means skipping it would strand a stale
    // visible=true document forever — but that document must carry visible=false.
    var track = CatalogTestFixtures.track("t6", "Taken Down Track", "a1", "Artist", 1L);

    var doc = CatalogIndexDocuments.fromTrack(track, false);

    assertEquals("t6", doc.entityId());
    assertFalse(doc.visible(), "a track gated by a non-live release must be indexed as hidden, not skipped");
  }

  @Test
  void track_with_null_plays_gets_zero_popularity_not_an_NPE() {
    var track = CatalogTestFixtures.track("t2", "Kwaku the Traveller", "black-sherif", "Black Sherif", null);

    var doc = CatalogIndexDocuments.fromTrack(track, true);

    assertEquals(0L, doc.popularity().score());
  }

  @Test
  void track_with_price_maps_price_minor_amount_and_currency() {
    // 550 pesewas -> 5.50 GHS: chosen so a wrong divisor or rounding mode would actually show up
    // (unlike e.g. 100 -> 1.00, where several broken implementations coincide with the correct one).
    var track = CatalogTestFixtures.track("t4", "For Sale Track", "a1", "Artist", 1L, 550L);

    var payload = CatalogIndexDocuments.fromTrack(track, true).payload();

    assertEquals(550L, payload.get("price_minor"));
    assertEquals(
        0,
        BigDecimal.valueOf(5.50).compareTo((BigDecimal) payload.get("price_amount")),
        "price_amount should be 5.50, was " + payload.get("price_amount"));
    assertEquals("GHS", payload.get("price_currency"));
    assertTrue(
        TRACK_ALLOWED.containsAll(payload.keySet()),
        "payload had keys SearchDocumentMapper would silently strip: " + payload.keySet());
  }

  @Test
  void track_without_price_omits_all_price_keys() {
    var track = CatalogTestFixtures.track("t5", "Free Track", "a1", "Artist", 1L, null);

    var payload = CatalogIndexDocuments.fromTrack(track, true).payload();

    assertFalse(payload.containsKey("price_minor"), "price_minor should be absent when track has no price");
    assertFalse(payload.containsKey("price_amount"), "price_amount should be absent when track has no price");
    assertFalse(payload.containsKey("price_currency"), "price_currency should be absent when track has no price");
  }

  @Test
  void track_payload_only_carries_allow_listed_keys() {
    var track = CatalogTestFixtures.track("t3", "Title", "a1", "Artist", 1L);

    var doc = CatalogIndexDocuments.fromTrack(track, true);

    assertTrue(
        TRACK_ALLOWED.containsAll(doc.payload().keySet()),
        "payload had keys SearchDocumentMapper would silently strip: " + doc.payload().keySet());
  }

  @Test
  void public_playlist_is_visible_and_private_playlist_is_indexed_but_hidden() {
    var publicPlaylist = CatalogTestFixtures.playlist("p1", "Vibes", "BeatzClik", true, 10L);
    var privatePlaylist = CatalogTestFixtures.playlist("p2", "My Private Playlist", "Me", false, 0L);

    assertTrue(CatalogIndexDocuments.fromPlaylist(publicPlaylist).visible());
    // Indexed, not omitted: reindex is upsert-only, so hiding must be expressed as visible=false.
    assertFalse(CatalogIndexDocuments.fromPlaylist(privatePlaylist).visible());
  }

  @Test
  void playlist_payload_only_carries_image() {
    var playlist = CatalogTestFixtures.playlist("p1", "Vibes", "BeatzClik", true, 10L);

    var keys = CatalogIndexDocuments.fromPlaylist(playlist).payload().keySet();

    assertTrue(PLAYLIST_ALLOWED.containsAll(keys), "unexpected playlist payload keys: " + keys);
  }

  @Test
  void artist_uses_monthly_listeners_for_popularity_and_is_visible() {
    var artist = CatalogTestFixtures.artist("a1", "Black Sherif", 9000L);

    var doc = CatalogIndexDocuments.fromArtist(artist);

    assertEquals(EntityType.ARTIST, doc.entityType());
    assertEquals("Black Sherif", doc.title());
    assertEquals(9000L, doc.popularity().score());
    assertTrue(doc.visible());
    assertTrue(ARTIST_ALLOWED.containsAll(doc.payload().keySet()), "unexpected artist payload keys");
  }

  @Test
  void artist_with_null_monthly_listeners_gets_zero_popularity_not_an_NPE() {
    var artist = CatalogTestFixtures.artist("a2", "Nobody", null);

    assertEquals(0L, CatalogIndexDocuments.fromArtist(artist).popularity().score());
  }

  @Test
  void album_has_no_popularity_source_so_it_is_zero() {
    var album = CatalogTestFixtures.album("al1", "Iron Boy", "black-sherif", "Black Sherif");

    var doc = CatalogIndexDocuments.fromAlbum(album);

    assertEquals(EntityType.ALBUM, doc.entityType());
    assertEquals("Black Sherif", doc.subtitle());
    assertEquals(0L, doc.popularity().score());
    assertTrue(doc.visible());
    assertTrue(ALBUM_ALLOWED.containsAll(doc.payload().keySet()), "unexpected album payload keys");
  }

  @Test
  void every_document_has_a_non_blank_title_and_id() {
    // IndexDocument's compact constructor rejects blank title/entityId — pin that we never build one.
    List<String> titles =
        List.of(
            CatalogIndexDocuments.fromTrack(CatalogTestFixtures.track("t1", "T", "a1", "A", 1L), true).title(),
            CatalogIndexDocuments.fromArtist(CatalogTestFixtures.artist("a1", "A", 1L)).title(),
            CatalogIndexDocuments.fromAlbum(CatalogTestFixtures.album("al1", "Al", "a1", "A")).title(),
            CatalogIndexDocuments.fromPlaylist(CatalogTestFixtures.playlist("p1", "P", "C", true, 1L)).title());

    titles.forEach(t -> assertFalse(t == null || t.isBlank(), "title must be non-blank"));
  }
}
