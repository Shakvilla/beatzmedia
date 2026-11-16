package org.shakvilla.beatzmedia.discovery.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetReleaseDiscoveryInfo;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.ShowId;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@Tag("integration")
class ContentOriginCountryIT {

  @Inject EntityManager em;
  @Inject GetReleaseDiscoveryInfo releases;
  @Inject StudioRepository studio;

  @Test
  @TestTransaction
  void persistsExplicitCountryAndLeavesLegacyContentUnassigned() {
    String suffix = UUID.randomUUID().toString();
    String artist = "artist-" + suffix;
    String release = "release-" + suffix;
    String show = "show-" + suffix;
    String legacy = "legacy-" + suffix;
    em.createNativeQuery("insert into artist_profile (id, name, image)"
        + " values (:id, 'Artist', '/image')")
        .setParameter("id", artist).executeUpdate();
    em.createNativeQuery("insert into release (id, artist_id, title, type, status, visibility,"
        + " went_live_at, origin_country) values (:id, :artist, 'Song', 'single', 'live',"
        + " 'public', :published, 'GH')")
        .setParameter("id", release).setParameter("artist", artist)
        .setParameter("published", Instant.parse("2026-11-06T12:00:00Z"))
        .executeUpdate();
    em.createNativeQuery("insert into studio_podcast_show"
        + " (id, artist_id, title, category, origin_country)"
        + " values (:id, :artist, 'Show', 'Culture', 'NG')")
        .setParameter("id", show).setParameter("artist", artist).executeUpdate();
    em.createNativeQuery("insert into studio_podcast_show (id, artist_id, title, category)"
        + " values (:id, :artist, 'Legacy', 'Culture')")
        .setParameter("id", legacy).setParameter("artist", artist).executeUpdate();

    assertEquals("GH", releases.get(new ReleaseId(release)).originCountry());
    assertEquals("NG", studio.findShow(new ArtistId(artist), new ShowId(show))
        .orElseThrow().originCountry());
    assertNull(studio.findShow(new ArtistId(artist), new ShowId(legacy))
        .orElseThrow().originCountry());
  }
}
