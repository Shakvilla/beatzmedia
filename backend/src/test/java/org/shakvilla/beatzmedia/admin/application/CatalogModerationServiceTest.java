package org.shakvilla.beatzmedia.admin.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.admin.application.port.in.CatalogItemDetailView;
import org.shakvilla.beatzmedia.admin.application.port.in.CatalogQuery;
import org.shakvilla.beatzmedia.admin.application.port.in.PagedCatalogView;
import org.shakvilla.beatzmedia.admin.application.port.out.CatalogAdminReader.CatalogDetailRow;
import org.shakvilla.beatzmedia.admin.application.port.out.CatalogAdminReader.SplitRow;
import org.shakvilla.beatzmedia.admin.application.port.out.CatalogAdminReader.TrackRow;
import org.shakvilla.beatzmedia.admin.application.port.out.ModerationCaseRepository;
import org.shakvilla.beatzmedia.admin.application.service.ApproveCatalogItemService;
import org.shakvilla.beatzmedia.admin.application.service.FlagCatalogItemService;
import org.shakvilla.beatzmedia.admin.application.service.GetCatalogItemService;
import org.shakvilla.beatzmedia.admin.application.service.ListCatalogModerationService;
import org.shakvilla.beatzmedia.admin.application.service.ReinstateCatalogItemService;
import org.shakvilla.beatzmedia.admin.application.service.TakedownCatalogItemService;
import org.shakvilla.beatzmedia.admin.domain.CatalogFilter;
import org.shakvilla.beatzmedia.admin.domain.ModerationCase;
import org.shakvilla.beatzmedia.admin.fakes.FakeCatalogAdminPort;
import org.shakvilla.beatzmedia.admin.fakes.FakeCatalogAdminReader;
import org.shakvilla.beatzmedia.admin.fakes.FakeModerationCaseRepository;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditReader;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.catalog.domain.IllegalTransitionException;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for the WU-ADM-3 catalog-moderation use-case services (LLFR-ADMIN-03.1/.2). Uses
 * fakes for all output ports. One test per acceptance criterion. Testing-strategy §2 / admin ADD
 * §11.
 */
@Tag("unit")
class CatalogModerationServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-11T10:00:00Z");
  private static final String ACTOR_ID = "account-admin";
  private static final String RELEASE_ID = "release-1";

  private FakeCatalogAdminReader catalogAdminReader;
  private FakeCatalogAdminPort catalogAdminPort;
  private FakeModerationCaseRepository moderationCaseRepository;
  private FakeAuditWriter auditWriter;
  private FakeAuditReader auditReader;
  private FakeIds ids;
  private FakeClock clock;

  private ListCatalogModerationService listCatalogModerationService;
  private GetCatalogItemService getCatalogItemService;
  private ApproveCatalogItemService approveCatalogItemService;
  private TakedownCatalogItemService takedownCatalogItemService;
  private ReinstateCatalogItemService reinstateCatalogItemService;
  private FlagCatalogItemService flagCatalogItemService;

  @BeforeEach
  void setUp() {
    catalogAdminReader = new FakeCatalogAdminReader();
    catalogAdminPort = new FakeCatalogAdminPort();
    moderationCaseRepository = new FakeModerationCaseRepository();
    auditWriter = new FakeAuditWriter();
    auditReader = new FakeAuditReader();
    ids = FakeIds.sequential("mod");
    clock = FakeClock.at(NOW);

    listCatalogModerationService = new ListCatalogModerationService(catalogAdminReader);
    getCatalogItemService = new GetCatalogItemService(catalogAdminReader, auditReader);
    approveCatalogItemService =
        new ApproveCatalogItemService(catalogAdminPort, catalogAdminReader, auditReader);
    takedownCatalogItemService =
        new TakedownCatalogItemService(catalogAdminPort, catalogAdminReader, auditReader);
    reinstateCatalogItemService =
        new ReinstateCatalogItemService(catalogAdminPort, catalogAdminReader, auditReader);
    flagCatalogItemService = new FlagCatalogItemService(
        catalogAdminReader, moderationCaseRepository, auditReader, auditWriter, ids, clock);

    catalogAdminReader.seed(new CatalogDetailRow(
        RELEASE_ID, "Iron Boy", "artist-1", "Black Sherif", "album", "in_review", NOW,
        List.of(new TrackRow(1, "track-1", "Track One", 180, 500)),
        List.of(new SplitRow("track-1", "Black Sherif", "Primary artist", 70, "confirmed"))));
  }

  // ---- ListCatalogModeration (LLFR-ADMIN-03.1) ----

  @Test
  void list_returns_items_and_real_whole_catalog_counts() {
    PagedCatalogView result =
        listCatalogModerationService.list(new CatalogQuery(null, null), PageRequest.defaults());

    assertEquals(1, result.items().size());
    assertEquals(1, result.counts().pending());
    assertEquals(0, result.counts().published());
    assertEquals(0, result.counts().takedown());
  }

  @Test
  void list_filters_by_status_bucket() {
    PagedCatalogView pending = listCatalogModerationService.list(
        new CatalogQuery(CatalogFilter.PENDING, null), PageRequest.defaults());
    PagedCatalogView published = listCatalogModerationService.list(
        new CatalogQuery(CatalogFilter.PUBLISHED, null), PageRequest.defaults());

    assertEquals(1, pending.items().size());
    assertEquals(0, published.items().size());
  }

  // ---- GetCatalogItem (LLFR-ADMIN-03.1) ----

  @Test
  void get_returns_tracklist_splits_and_null_isrc_upc_note() {
    CatalogItemDetailView view = getCatalogItemService.get(RELEASE_ID);

    assertEquals(RELEASE_ID, view.id());
    assertEquals(1, view.tracklist().size());
    assertNull(view.tracklist().get(0).isrc(), "isrc is honest null (Category B)");
    assertNull(view.upc(), "upc is honest null (Category B)");
    assertNull(view.note(), "note is honest null (Category B)");
    assertEquals(1, view.splits().size());
    assertEquals(70, view.splits().get(0).percent());
  }

  @Test
  void get_unknown_release_throws_ReleaseNotFoundException() {
    assertThrows(ReleaseNotFoundException.class, () -> getCatalogItemService.get("no-such-release"));
  }

  // ---- ApproveCatalogItem (LLFR-ADMIN-03.2) ----

  @Test
  void approve_forwards_to_catalog_admin_port_and_writes_no_second_audit_entry() {
    approveCatalogItemService.approve(ACTOR_ID, RELEASE_ID, Optional.empty());

    FakeCatalogAdminPort.Call call = catalogAdminPort.lastCall();
    assertEquals(ACTOR_ID, call.actorId());
    assertEquals(RELEASE_ID, call.releaseId());
    assertTrue(call.goLiveAt().isEmpty());
    assertEquals(0, auditWriter.size(),
        "catalog's PublishReleaseService self-audits; admin must NOT write a second AuditEntry");
  }

  @Test
  void approve_propagates_illegal_transition_from_catalog() {
    catalogAdminPort.failNextCallWith(() -> new IllegalTransitionException("already live"));
    assertThrows(IllegalTransitionException.class,
        () -> approveCatalogItemService.approve(ACTOR_ID, RELEASE_ID, Optional.empty()));
  }

  // ---- TakedownCatalogItem (LLFR-ADMIN-03.2) ----

  @Test
  void takedown_forwards_reason_and_writes_no_second_audit_entry() {
    takedownCatalogItemService.takedown(ACTOR_ID, RELEASE_ID, "Copyright claim");

    assertEquals("Copyright claim", catalogAdminPort.lastCall().reason());
    assertEquals(0, auditWriter.size());
  }

  // ---- ReinstateCatalogItem ----

  @Test
  void reinstate_forwards_to_catalog_admin_port_and_writes_no_second_audit_entry() {
    reinstateCatalogItemService.reinstate(ACTOR_ID, RELEASE_ID);

    assertEquals(RELEASE_ID, catalogAdminPort.lastCall().releaseId());
    assertEquals(0, auditWriter.size());
  }

  // ---- FlagCatalogItem (LLFR-ADMIN-03.2) ----

  @Test
  void flag_opens_a_moderation_case_and_appends_exactly_one_audit_entry() {
    CatalogItemDetailView view =
        flagCatalogItemService.flag(ACTOR_ID, RELEASE_ID, Optional.of("duplicate ISRC"));

    assertEquals(RELEASE_ID, view.id());
    assertEquals(1, auditWriter.size(), "flag is admin-owned; admin writes exactly one AuditEntry");
    assertEquals("Flagged release", auditWriter.all().get(0).getAction());
    assertEquals("duplicate ISRC", auditWriter.all().get(0).getReason());

    ModerationCaseRepository.Summary summary = moderationCaseRepository.summary();
    assertEquals(1, summary.openCount());
  }

  @Test
  void flag_creates_case_targeting_release_with_release_prefix() {
    flagCatalogItemService.flag(ACTOR_ID, RELEASE_ID, Optional.empty());

    ModerationCase created = moderationCaseRepository.list(null, null, PageRequest.defaults())
        .items().get(0);
    assertEquals("release:" + RELEASE_ID, created.getTargetRef());
    assertFalse(created.isEscalated());
  }

  @Test
  void flag_unknown_release_throws_ReleaseNotFoundException_and_opens_no_case() {
    assertThrows(ReleaseNotFoundException.class,
        () -> flagCatalogItemService.flag(ACTOR_ID, "no-such-release", Optional.empty()));
    assertEquals(0, moderationCaseRepository.summary().openCount());
  }
}
