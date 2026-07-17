package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.catalog.application.port.in.CreateReleaseDraft.CreateDraftCommand;
import org.shakvilla.beatzmedia.catalog.application.port.in.StudioReleaseDetailView;
import org.shakvilla.beatzmedia.catalog.application.service.CreateReleaseDraftService;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;
import org.shakvilla.beatzmedia.catalog.domain.Visibility;
import org.shakvilla.beatzmedia.catalog.fakes.FakeCatalogRepository;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for {@link CreateReleaseDraftService}. Covers WU-CAT-5 create-draft acceptance
 * criteria. No framework; plain JUnit 5.
 */
@Tag("unit")
class CreateReleaseDraftServiceTest {

  private FakeCatalogRepository repo;
  private FakeAuditWriter auditWriter;
  private CreateReleaseDraftService service;

  private static final ArtistId ARTIST = new ArtistId("artist-1");

  @BeforeEach
  void setUp() {
    repo = new FakeCatalogRepository();
    auditWriter = new FakeAuditWriter();
    service = new CreateReleaseDraftService(
        repo, FakeIds.sequential("rel"), FakeClock.fixed(), auditWriter);
  }

  @Test
  void create_returnsDraftDetailViewAndPersistsDraftRelease() {
    CreateDraftCommand cmd = new CreateDraftCommand(
        ARTIST, "My EP", ReleaseType.ep, Visibility.PUBLIC, null, "Afrobeats", "bio");

    StudioReleaseDetailView view = service.create(cmd);

    assertNotNull(view.id());
    assertEquals(ReleaseStatus.draft, view.status());
    assertEquals("My EP", view.title());
    assertTrue(view.tracks().isEmpty());
    assertEquals("Afrobeats", view.genre());

    var stored = repo.findRelease(new org.shakvilla.beatzmedia.catalog.domain.ReleaseId(view.id()));
    assertTrue(stored.isPresent());
    assertEquals(ReleaseStatus.draft, stored.get().getStatus());
  }

  @Test
  void create_blankTitle_defaultsToUntitledRelease() {
    CreateDraftCommand cmd = new CreateDraftCommand(
        ARTIST, "  ", ReleaseType.single, Visibility.PUBLIC, null, null, null);

    StudioReleaseDetailView view = service.create(cmd);

    assertEquals("Untitled release", view.title());
  }

  @Test
  void create_appendsCreateDraftAuditEntry() {
    CreateDraftCommand cmd = new CreateDraftCommand(
        ARTIST, "My EP", ReleaseType.ep, Visibility.PUBLIC, null, null, null);

    service.create(cmd);

    assertEquals(1, auditWriter.size());
    assertEquals("CREATE_DRAFT", auditWriter.all().get(0).getAction());
    assertEquals(ARTIST.value(), auditWriter.all().get(0).getActor());
  }
}
