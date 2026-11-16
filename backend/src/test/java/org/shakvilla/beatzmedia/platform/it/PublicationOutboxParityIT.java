package org.shakvilla.beatzmedia.platform.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.adapter.out.scheduler.DurableEventDispatchJob;
import org.shakvilla.beatzmedia.platform.application.port.out.DurableEventPublisher;
import org.shakvilla.beatzmedia.platform.domain.DurableEventMessage;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;

/** Proves the outbox-only cutover produces the same fan-facing release and podcast projections. */
@QuarkusTest
@Tag("integration")
class PublicationOutboxParityIT {

  @Inject AgroalDataSource dataSource;
  @Inject DurableEventPublisher publisher;
  @Inject DurableEventDispatchJob dispatcher;

  @Test
  void dispatchProjectsReleaseAndPodcastOnlyAfterDurableEventsAreClaimed() {
    String suffix = Long.toUnsignedString(System.nanoTime());
    String artistId = "parity-artist-" + suffix;
    String releaseId = "parity-release-" + suffix;
    String trackId = "parity-track-" + suffix;
    String showId = "parity-show-" + suffix;
    String episodeId = "parity-episode-" + suffix;
    String releaseEventId = "parity-release-event-" + suffix;
    String episodeEventId = "parity-episode-event-" + suffix;
    Instant occurredAt = Instant.now().minusSeconds(1);

    seedLiveRelease(artistId, releaseId, trackId);
    seedPublishedEpisode(artistId, showId, episodeId, occurredAt);

    publisher.append(new DurableEventMessage(
        releaseEventId,
        "catalog.release-went-live",
        1,
        "release",
        releaseId,
        Map.of(
            "releaseId", releaseId,
            "artistId", artistId,
            "wentLiveAt", occurredAt.toString()),
        occurredAt));
    publisher.append(new DurableEventMessage(
        episodeEventId,
        "studio.episode-published",
        1,
        "episode",
        episodeId,
        Map.of(
            "episodeId", episodeId,
            "showId", showId,
            "artistId", artistId,
            "occurredAt", occurredAt.toString()),
        occurredAt));

    assertFalse(exists("select 1 from album where id = ?", releaseId));
    assertFalse(exists(
        "select 1 from search_document where entity_type = 'ALBUM' and entity_id = ? and visible",
        releaseId));
    assertEquals("free", text("select ownership from track where id = ?", trackId));
    assertFalse(exists("select 1 from podcast where id = ?", showId));
    assertFalse(exists("select 1 from podcast_episode where id = ?", episodeId));
    assertEquals("pending", outboxStatus(releaseEventId));
    assertEquals("pending", outboxStatus(episodeEventId));

    dispatchUntilCompleted(releaseEventId, episodeEventId);

    assertTrue(exists("select 1 from album where id = ?", releaseId));
    assertTrue(exists(
        "select 1 from search_document where entity_type = 'ALBUM' and entity_id = ? and visible",
        releaseId));
    assertTrue(exists(
        "select 1 from search_document where entity_type = 'TRACK' and entity_id = ? and visible",
        trackId));
    assertEquals("for-sale", text("select ownership from track where id = ?", trackId));
    assertEquals("700", text("select price_minor from track where id = ?", trackId));
    assertTrue(exists("select 1 from podcast where id = ?", showId));
    assertTrue(exists("select 1 from podcast_episode where id = ?", episodeId));
    assertEquals("1", text("select episode_count from podcast where id = ?", showId));
    assertEquals("completed", outboxStatus(releaseEventId));
    assertEquals("completed", outboxStatus(episodeEventId));
  }

  private void dispatchUntilCompleted(String firstId, String secondId) {
    for (int attempt = 0; attempt < 5; attempt++) {
      dispatcher.runOnce();
      if ("completed".equals(outboxStatus(firstId))
          && "completed".equals(outboxStatus(secondId))) {
        return;
      }
    }
    assertEquals("completed", outboxStatus(firstId));
    assertEquals("completed", outboxStatus(secondId));
  }

  private void seedLiveRelease(String artistId, String releaseId, String trackId) {
    execute(
        "insert into artist_profile (id, name, image, verified, monthly_listeners, followers,"
            + " genres, created_at, updated_at) values (?, 'Parity Artist', '/parity.jpg', false,"
            + " 0, 0, '{}', now(), now())",
        artistId);
    execute(
        "insert into track (id, title, artist_id, artist_name, duration_sec, image, ownership,"
            + " price_minor, plays, status) values (?, 'Parity Track', ?, 'Parity Artist', 180,"
            + " '/parity.jpg', 'free', null, 0, 'ready')",
        trackId,
        artistId);
    execute(
        "insert into release (id, artist_id, title, type, status, visibility, went_live_at,"
            + " list_price_minor, created_at, updated_at, downloadable) values (?, ?,"
            + " 'Parity Release', 'single', 'live', 'public', now(), 700, now(), now(), true)",
        releaseId,
        artistId);
    execute(
        "insert into release_track (release_id, track_id, position, price_minor)"
            + " values (?, ?, 0, 700)",
        releaseId,
        trackId);
  }

  private void seedPublishedEpisode(
      String artistId, String showId, String episodeId, Instant publishedAt) {
    execute(
        "insert into studio_podcast_show (id, artist_id, title, category, image, description,"
            + " created_at) values (?, ?, 'Parity Show', 'Storytelling', '/show.jpg',"
            + " 'Parity description', now())",
        showId,
        artistId);
    execute(
        "insert into studio_episode (id, show_id, artist_id, title, description, audio_key,"
            + " cover_url, duration_sec, status, is_premium, price_minor, currency,"
            + " is_early_access, published_at, plays, created_at) values (?, ?, ?,"
            + " 'Parity Episode', 'Parity episode description', 'asset-parity', null, 240,"
            + " 'published', false, 0, 'GHS', false, ?, 0, now())",
        episodeId,
        showId,
        artistId,
        publishedAt);
  }

  private void execute(String sql, Object... parameters) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < parameters.length; i++) {
        if (parameters[i] instanceof Instant instant) {
          statement.setTimestamp(i + 1, java.sql.Timestamp.from(instant));
        } else {
          statement.setObject(i + 1, parameters[i]);
        }
      }
      statement.executeUpdate();
    } catch (Exception failure) {
      throw new RuntimeException("seed failed: " + sql, failure);
    }
  }

  private boolean exists(String sql, String id) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, id);
      try (ResultSet result = statement.executeQuery()) {
        return result.next();
      }
    } catch (Exception failure) {
      throw new RuntimeException("query failed: " + sql, failure);
    }
  }

  private String outboxStatus(String id) {
    return text("select status from platform_outbox_event where id = ?", id);
  }

  private String text(String sql, String id) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, id);
      try (ResultSet result = statement.executeQuery()) {
        assertTrue(result.next(), "expected one row for " + id);
        return result.getString(1);
      }
    } catch (Exception failure) {
      throw new RuntimeException("query failed: " + sql, failure);
    }
  }
}
