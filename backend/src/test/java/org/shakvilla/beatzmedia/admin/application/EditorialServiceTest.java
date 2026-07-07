package org.shakvilla.beatzmedia.admin.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.admin.application.port.in.CreateCuratedPlaylist;
import org.shakvilla.beatzmedia.admin.application.port.in.SaveFeaturedSlots;
import org.shakvilla.beatzmedia.admin.application.port.in.SchedulePushItem;
import org.shakvilla.beatzmedia.admin.application.service.CreateCuratedPlaylistService;
import org.shakvilla.beatzmedia.admin.application.service.ListCuratedPlaylistsService;
import org.shakvilla.beatzmedia.admin.application.service.ListFeaturedSlotsService;
import org.shakvilla.beatzmedia.admin.application.service.ListPushItemsService;
import org.shakvilla.beatzmedia.admin.application.service.SaveFeaturedSlotsService;
import org.shakvilla.beatzmedia.admin.application.service.SchedulePushItemService;
import org.shakvilla.beatzmedia.admin.domain.BlankFeaturedSlotTitleException;
import org.shakvilla.beatzmedia.admin.domain.BlankPlaylistNameException;
import org.shakvilla.beatzmedia.admin.domain.BlankPushItemFieldException;
import org.shakvilla.beatzmedia.admin.domain.CuratedPlaylist;
import org.shakvilla.beatzmedia.admin.domain.FeaturedSlot;
import org.shakvilla.beatzmedia.admin.domain.PushItem;
import org.shakvilla.beatzmedia.admin.fakes.FakeCuratedPlaylistRepository;
import org.shakvilla.beatzmedia.admin.fakes.FakeFeaturedSlotRepository;
import org.shakvilla.beatzmedia.admin.fakes.FakePushItemRepository;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for the WU-ADM-4 editorial use-case services. Uses fakes for all output ports. One
 * test per LLFR-ADMIN-06.1 acceptance criterion. Testing-strategy §2 / admin ADD §11.
 */
@Tag("unit")
class EditorialServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-07T10:00:00Z");
  private static final String ACTOR_ID = "account-editor";

  private FakeFeaturedSlotRepository featuredRepo;
  private FakePushItemRepository pushRepo;
  private FakeCuratedPlaylistRepository playlistRepo;
  private FakeAuditWriter auditWriter;
  private FakeIds ids;
  private FakeClock clock;

  private ListFeaturedSlotsService listFeaturedService;
  private SaveFeaturedSlotsService saveFeaturedService;
  private ListPushItemsService listPushService;
  private SchedulePushItemService scheduleePushService;
  private ListCuratedPlaylistsService listPlaylistsService;
  private CreateCuratedPlaylistService createPlaylistService;

  @BeforeEach
  void setUp() {
    featuredRepo = new FakeFeaturedSlotRepository();
    pushRepo = new FakePushItemRepository();
    playlistRepo = new FakeCuratedPlaylistRepository();
    auditWriter = new FakeAuditWriter();
    ids = FakeIds.sequential("ed");
    clock = FakeClock.at(NOW);

    listFeaturedService = new ListFeaturedSlotsService(featuredRepo);
    saveFeaturedService = new SaveFeaturedSlotsService(featuredRepo, auditWriter, ids, clock);
    listPushService = new ListPushItemsService(pushRepo);
    scheduleePushService = new SchedulePushItemService(pushRepo, auditWriter, ids, clock);
    listPlaylistsService = new ListCuratedPlaylistsService(playlistRepo);
    createPlaylistService = new CreateCuratedPlaylistService(playlistRepo, auditWriter, ids, clock);

    featuredRepo.seed(new FeaturedSlot("f1", 1, "Trending in Ghana", "updated daily", false));
    featuredRepo.seed(new FeaturedSlot("f2", 2, "Made in Ghana 2026", "manual · 64 tracks", false));
  }

  // ---- ListFeaturedSlots (LLFR-ADMIN-06.1: GET /admin/editorial/featured) ----

  @Test
  void list_returns_slots_ordered_by_position() {
    List<FeaturedSlot> slots = listFeaturedService.list();
    assertEquals(2, slots.size());
    assertEquals("f1", slots.get(0).getId());
    assertEquals("f2", slots.get(1).getId());
  }

  // ---- SaveFeaturedSlots (LLFR-ADMIN-06.1: PUT /admin/editorial/featured, ordered) ----

  @Test
  void save_replaces_full_set_and_reassigns_positions_from_order() {
    List<SaveFeaturedSlots.FeaturedSlotInput> ordered = List.of(
        new SaveFeaturedSlots.FeaturedSlotInput("f2", "Made in Ghana 2026", "manual · 64 tracks", false),
        new SaveFeaturedSlots.FeaturedSlotInput("f1", "Trending in Ghana", "updated daily", false),
        new SaveFeaturedSlots.FeaturedSlotInput(null, "Iron Boy · Black Sherif", "sponsored slot", true));

    List<FeaturedSlot> saved = saveFeaturedService.save(ACTOR_ID, ordered);

    assertEquals(3, saved.size());
    assertEquals("f2", saved.get(0).getId());
    assertEquals(1, saved.get(0).getPosition());
    assertEquals("f1", saved.get(1).getId());
    assertEquals(2, saved.get(1).getPosition());
    assertTrue(saved.get(2).isSponsored());
    assertEquals(3, saved.get(2).getPosition());
  }

  @Test
  void save_audits_exactly_once_of_type_editorial() {
    List<SaveFeaturedSlots.FeaturedSlotInput> ordered =
        List.of(new SaveFeaturedSlots.FeaturedSlotInput("f1", "Trending in Ghana", "updated daily", false));

    saveFeaturedService.save(ACTOR_ID, ordered);

    assertEquals(1, auditWriter.size(), "exactly one AuditEntry per mutation (INV-10)");
    assertEquals(AuditType.EDITORIAL, auditWriter.all().get(0).getType());
    assertEquals(ACTOR_ID, auditWriter.all().get(0).getActor());
  }

  @Test
  void save_with_blank_title_throws_422_before_any_state_change_or_audit() {
    List<SaveFeaturedSlots.FeaturedSlotInput> ordered =
        List.of(new SaveFeaturedSlots.FeaturedSlotInput("f1", "  ", "note", false));

    assertThrows(BlankFeaturedSlotTitleException.class,
        () -> saveFeaturedService.save(ACTOR_ID, ordered));

    assertEquals(2, featuredRepo.listOrdered().size(), "original slots unchanged");
    assertEquals(0, auditWriter.size(), "no audit row on a rejected mutation");
  }

  // ---- ListPushItems (LLFR-ADMIN-06.1: GET /admin/editorial/push) ----

  @Test
  void list_push_returns_scheduled_items_ordered_by_scheduledAt() {
    scheduleePushService.schedule(ACTOR_ID,
        new SchedulePushItem.PushItemInput("Fri", "9AM", "Friday drops", "1.4M", NOW.plusSeconds(3600)));
    scheduleePushService.schedule(ACTOR_ID,
        new SchedulePushItem.PushItemInput("Mon", "6PM", "Iron Boy out now", "1.2M", NOW));

    List<PushItem> items = listPushService.list();
    assertEquals(2, items.size());
    assertEquals("Iron Boy out now", items.get(0).getTitle());
    assertEquals("Friday drops", items.get(1).getTitle());
  }

  // ---- SchedulePushItem (LLFR-ADMIN-06.1: POST /admin/editorial/push) ----

  @Test
  void schedule_push_persists_and_audits_exactly_once() {
    PushItem item = scheduleePushService.schedule(ACTOR_ID,
        new SchedulePushItem.PushItemInput("Wed", "12PM", "Wednesday Wrap-up", "All users", NOW));

    assertEquals("Wed", item.getDay());
    assertEquals("12PM", item.getTimeLabel());
    assertEquals(1, pushRepo.size());
    assertEquals(1, auditWriter.size(), "exactly one AuditEntry per mutation (INV-10)");
    assertEquals(AuditType.EDITORIAL, auditWriter.all().get(0).getType());
  }

  @Test
  void schedule_push_with_blank_field_throws_422_before_any_persistence_or_audit() {
    assertThrows(BlankPushItemFieldException.class, () -> scheduleePushService.schedule(
        ACTOR_ID, new SchedulePushItem.PushItemInput("", "12PM", "Title", "All users", NOW)));

    assertEquals(0, pushRepo.size(), "no push item persisted on a rejected mutation");
    assertEquals(0, auditWriter.size(), "no audit row on a rejected mutation");
  }

  @Test
  void schedule_push_allows_null_scheduledAt() {
    PushItem item = scheduleePushService.schedule(ACTOR_ID,
        new SchedulePushItem.PushItemInput("Sat", "8PM", "Live acoustic", "Followers", null));
    assertEquals(null, item.getScheduledAt());
  }

  // ---- ListCuratedPlaylists (LLFR-ADMIN-06.1: GET /admin/editorial/playlists) ----

  @Test
  void list_playlists_returns_all_ordered_by_name() {
    createPlaylistService.create(ACTOR_ID, new CreateCuratedPlaylist.CuratedPlaylistInput("Hiplife Hour"));
    createPlaylistService.create(ACTOR_ID, new CreateCuratedPlaylist.CuratedPlaylistInput("Drill Wave"));

    List<CuratedPlaylist> playlists = listPlaylistsService.list();
    assertEquals(2, playlists.size());
    assertEquals("Drill Wave", playlists.get(0).getName());
    assertEquals("Hiplife Hour", playlists.get(1).getName());
  }

  // ---- CreateCuratedPlaylist (LLFR-ADMIN-06.1: POST /admin/editorial/playlists) ----

  @Test
  void create_playlist_persists_and_audits_exactly_once() {
    CuratedPlaylist playlist = createPlaylistService.create(
        ACTOR_ID, new CreateCuratedPlaylist.CuratedPlaylistInput("Made in Ghana"));

    assertEquals("Made in Ghana", playlist.getName());
    assertEquals(1, playlistRepo.size());
    assertEquals(1, auditWriter.size(), "exactly one AuditEntry per mutation (INV-10)");
    assertEquals(AuditType.EDITORIAL, auditWriter.all().get(0).getType());
  }

  @Test
  void create_playlist_with_blank_name_throws_422_before_any_persistence_or_audit() {
    assertThrows(BlankPlaylistNameException.class, () -> createPlaylistService.create(
        ACTOR_ID, new CreateCuratedPlaylist.CuratedPlaylistInput("   ")));

    assertEquals(0, playlistRepo.size(), "no playlist persisted on a rejected mutation");
    assertEquals(0, auditWriter.size(), "no audit row on a rejected mutation");
  }
}
