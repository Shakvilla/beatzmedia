package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.catalog.application.port.in.UploadReleaseTrack.AudioUpload;
import org.shakvilla.beatzmedia.catalog.application.port.in.UploadedTrackView;
import org.shakvilla.beatzmedia.catalog.application.service.UploadReleaseTrackService;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;
import org.shakvilla.beatzmedia.catalog.domain.Visibility;
import org.shakvilla.beatzmedia.catalog.fakes.FakeCatalogRepository;
import org.shakvilla.beatzmedia.media.application.port.in.UploadCommand;
import org.shakvilla.beatzmedia.media.application.port.in.UploadOriginalUseCase;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.MediaHandle;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.MediaStatus;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for {@link UploadReleaseTrackService}. Covers WU-CAT-5 review fix 5: INV-10 audit on
 * upload-attach (previously missing, asymmetric with {@code RemoveReleaseTrackService}). No
 * framework; plain JUnit 5.
 */
@Tag("unit")
class UploadReleaseTrackServiceTest {

  private static final ArtistId ARTIST = new ArtistId("artist-1");
  private static final java.time.Instant NOW = java.time.Instant.parse("2026-07-17T10:00:00Z");

  private FakeCatalogRepository repo;
  private FakeAuditWriter auditWriter;
  private UploadReleaseTrackService service;

  @BeforeEach
  void setUp() {
    repo = new FakeCatalogRepository();
    auditWriter = new FakeAuditWriter();
    UploadOriginalUseCase stubMedia = command -> new MediaHandle(
        new MediaAssetId("asset-1"), MediaKind.AUDIO, 180, MediaStatus.UPLOADING);
    service = new UploadReleaseTrackService(
        repo, stubMedia, FakeIds.sequential("trk"), FakeClock.at(NOW), auditWriter);
  }

  private Release draft() {
    Release r = Release.createDraft(
        "r1", ARTIST.value(), "Draft Title", ReleaseType.single,
        Visibility.PUBLIC, null, null, null, NOW);
    repo.addRelease(r);
    return r;
  }

  @Test
  void upload_attachesTrackAndAppendsAuditEntry() {
    draft();
    InputStream body = new ByteArrayInputStream(new byte[] {1, 2, 3});
    AudioUpload upload = new AudioUpload("song.wav", "audio/wav", 3L, body, "hash-1");

    UploadedTrackView view = service.upload(new ReleaseId("r1"), ARTIST, upload);

    assertEquals(0, view.position());
    Release updated = repo.findRelease(new ReleaseId("r1")).orElseThrow();
    assertEquals(1, updated.getTracks().size());

    // INV-10: exactly one audit entry for the upload-attach mutation, in the same call.
    assertEquals(1, auditWriter.all().size());
    var entry = auditWriter.all().get(0);
    assertEquals("UPLOAD_RELEASE_TRACK", entry.getAction());
    assertEquals("Release", entry.getTargetType());
    assertEquals("r1", entry.getTargetId());
    assertEquals(ARTIST.value(), entry.getActor());
  }
}
