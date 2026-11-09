package org.shakvilla.beatzmedia.studio.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.platform.domain.NotFoundException;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;
import org.shakvilla.beatzmedia.studio.application.service.SetShowOriginCountryService;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.PodcastShow;
import org.shakvilla.beatzmedia.studio.domain.ShowId;
import org.shakvilla.beatzmedia.studio.fakes.FakeStudioRepository;

@Tag("unit")
class SetShowOriginCountryServiceTest {

  @Test
  void assignsCountryOnlyToOwnedShowAndAuditsTheMutation() {
    ArtistId owner = new ArtistId("artist-1");
    ShowId showId = new ShowId("show-1");
    FakeStudioRepository repository = new FakeStudioRepository().withShow(PodcastShow.create(
        showId, owner, "Show", "Culture", "/cover", null, FakeClock.fixed().now()));
    List<AuditEntry> entries = new ArrayList<>();
    var service = new SetShowOriginCountryService(
        repository, entries::add, FakeIds.sequential("audit"), FakeClock.fixed());

    assertEquals("GH", service.set(owner, showId, " gh ").originCountry());
    assertEquals("GH", repository.findShow(owner, showId).orElseThrow().originCountry());
    assertEquals(1, entries.size());
    assertThrows(NotFoundException.class,
        () -> service.set(new ArtistId("another"), showId, "NG"));
    assertThrows(ValidationException.class, () -> service.set(owner, showId, "ZZ"));
    assertEquals(1, entries.size());
  }
}
