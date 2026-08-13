package org.shakvilla.beatzmedia.catalog.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.OwnershipStatus;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;

import io.quarkus.test.junit.QuarkusTest;

/**
 * {@code track.ownership} is a text column whose wire form is hyphenated — the check constraint
 * allows {@code 'owned' | 'free' | 'for-sale'}, and every reader compares against the hyphen:
 * commerce's {@code CatalogPricingServiceAdapter}, {@code CatalogExpansionReaderAdapter} and
 * playback's {@code CatalogReaderAdapter}. The Java enum constant is {@code for_sale}, so the
 * encoding is asymmetric and the writer must translate.
 *
 * <p>The read path always did ({@code valueOf(e.ownership.replace('-','_'))}); the write path did
 * not. It went unnoticed because nothing in production ever assigned {@code for_sale} — the two
 * values that were written, {@code free} and {@code owned}, encode to themselves. The first write
 * that mattered failed against the check constraint and rolled the projection back, so a priced
 * track silently stayed free and unpurchasable.
 *
 * <p>A round-trip assertion alone would not catch this: a symmetric-but-wrong encoding survives
 * write-then-read. The load-bearing assertion is the raw column value, because that string is what
 * the other modules compare against.
 */
@QuarkusTest
@Tag("integration")
class TrackOwnershipPersistenceIT {

  private static final String TRACK_ID = "for-sale-encoding-track";
  /** Seeded artist, reused for the {@code artist_id} FK. */
  private static final String ARTIST_ID = "burna-boy";

  @Inject CatalogRepository repository;
  @Inject EntityManager em;

  @AfterEach
  @Transactional
  void removeFixture() {
    em.createNativeQuery("DELETE FROM track WHERE id = ?1").setParameter(1, TRACK_ID).executeUpdate();
  }

  private Track freeStub() {
    return new Track(
        new TrackId(TRACK_ID), "Encoding Fixture", new ArtistId(ARTIST_ID), "Burna Boy",
        null, null, 180, "/images/placeholder.jpg", OwnershipStatus.free, null, 0L,
        null, null, null, null, "ready");
  }

  @Test
  @Transactional
  void aForSaleTrackIsStoredWithTheHyphenatedWireValue() {
    repository.saveTrack(freeStub().withReleasePricing(250));
    em.flush();
    em.clear();

    Object stored = em.createNativeQuery("SELECT ownership FROM track WHERE id = ?1")
        .setParameter(1, TRACK_ID)
        .getSingleResult();

    assertEquals("for-sale", stored,
        "readers in commerce and playback compare against the hyphenated wire value; "
            + "the enum's own name() would also violate track_ownership_chk");
  }

  @Test
  @Transactional
  void aForSaleTrackRoundTripsBackToTheEnum() {
    repository.saveTrack(freeStub().withReleasePricing(250));
    em.flush();
    em.clear();

    Track reloaded = repository.findTrack(new TrackId(TRACK_ID)).orElseThrow();

    assertEquals(OwnershipStatus.for_sale, reloaded.getOwnership());
    assertEquals(250L, reloaded.getPriceMinor().orElseThrow());
  }
}
