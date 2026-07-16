package org.shakvilla.beatzmedia.catalog.adapter.out.search;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

import org.shakvilla.beatzmedia.catalog.domain.Album;
import org.shakvilla.beatzmedia.catalog.domain.ArtistProfile;
import org.shakvilla.beatzmedia.catalog.domain.Playlist;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.search.domain.EntityType;
import org.shakvilla.beatzmedia.search.domain.IndexDocument;
import org.shakvilla.beatzmedia.search.domain.Popularity;

/**
 * Maps catalog domain entities to search {@link IndexDocument}s. Pure and stateless so it can be
 * unit-tested without CDI or a database.
 *
 * <p>Payload keys are restricted to what {@code SearchDocumentMapper.ALLOWED_PAYLOAD_KEYS} permits
 * for each type — anything else is silently dropped on persistence, so emitting it would be a lie.
 *
 * <p>{@code visible} is the only thing gating what search returns: the catalog repository applies no
 * visibility filter of its own, and {@code PostgresFtsSearchAdapter} queries {@code WHERE visible =
 * true}. Entities that must not surface are indexed with {@code visible=false} rather than skipped,
 * because reindex is upsert-only and a skipped entity would keep any stale visible document forever.
 */
public final class CatalogIndexDocuments {

  private CatalogIndexDocuments() {}

  public static IndexDocument fromTrack(Track track) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("image", track.getImage());
    payload.put("duration_sec", track.getDurationSec());
    track.getPriceMinor().ifPresent(minor -> putPrice(payload, minor));
    track.getQuality().ifPresent(q -> payload.put("quality", q));

    return new IndexDocument(
        EntityType.TRACK,
        track.getId().value(),
        track.getTitle(),
        track.getArtistName(),
        track.getTitle() + " " + track.getArtistName(),
        new Popularity(track.getPlays().orElse(0L)),
        true,
        payload);
  }

  public static IndexDocument fromArtist(ArtistProfile artist) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("image", artist.getImage());
    if (artist.getGenres() != null && !artist.getGenres().isEmpty()) {
      payload.put("genre", artist.getGenres().get(0));
    }

    return new IndexDocument(
        EntityType.ARTIST,
        artist.getId().value(),
        artist.getName(),
        artist.getLocation(),
        artist.getName(),
        new Popularity(artist.getMonthlyListeners() == null ? 0L : artist.getMonthlyListeners()),
        true,
        payload);
  }

  public static IndexDocument fromAlbum(Album album) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("image", album.getCoverImage());
    putPrice(payload, album.getListPriceMinor());
    if (album.getGenres() != null && !album.getGenres().isEmpty()) {
      payload.put("genre", album.getGenres().get(0));
    }

    return new IndexDocument(
        EntityType.ALBUM,
        album.getId().value(),
        album.getTitle(),
        album.getArtistName(),
        album.getTitle() + " " + album.getArtistName(),
        Popularity.ZERO,
        true,
        payload);
  }

  public static IndexDocument fromPlaylist(Playlist playlist) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("image", playlist.getImage());

    return new IndexDocument(
        EntityType.PLAYLIST,
        playlist.getId().value(),
        playlist.getTitle(),
        playlist.getCreator(),
        playlist.getTitle() + " " + playlist.getCreator(),
        new Popularity(playlist.getFollowers() == null ? 0L : playlist.getFollowers()),
        playlist.isPublic(),
        payload);
  }

  private static void putPrice(Map<String, Object> payload, long priceMinor) {
    payload.put("price_minor", priceMinor);
    payload.put(
        "price_amount", BigDecimal.valueOf(priceMinor).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
    payload.put("price_currency", "GHS");
  }
}
