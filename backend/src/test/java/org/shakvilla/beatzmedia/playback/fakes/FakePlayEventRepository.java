package org.shakvilla.beatzmedia.playback.fakes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.playback.application.port.out.PlayEventRepository;
import org.shakvilla.beatzmedia.playback.domain.PlayEvent;

/** In-memory fake for {@link PlayEventRepository} used in unit tests. */
public class FakePlayEventRepository implements PlayEventRepository {

  private final List<PlayEvent> events = new ArrayList<>();

  @Override
  public void insert(PlayEvent event) {
    events.add(event);
  }

  @Override
  public Optional<Instant> lastPlayAt(AccountId account, TrackId track) {
    return events.stream()
        .filter(
            e ->
                e.getAccountId().map(a -> a.equals(account)).orElse(false)
                    && e.getTrackId().equals(track))
        .map(PlayEvent::getAt)
        .max(Instant::compareTo);
  }

  public List<PlayEvent> events() {
    return events;
  }

  public int size() {
    return events.size();
  }
}
