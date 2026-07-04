package org.shakvilla.beatzmedia.playback.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.playback.application.port.out.PlayEventRepository;
import org.shakvilla.beatzmedia.playback.domain.PlayEvent;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;

/**
 * Panache JPA implementation of {@link PlayEventRepository}. Owns the {@code play_event} table —
 * no other module reads it directly. Playback ADD §5.2/§7.
 */
@ApplicationScoped
public class PlayEventPanacheRepository
    implements PlayEventRepository, PanacheRepositoryBase<PlayEventEntity, String> {

  @Override
  public void insert(PlayEvent event) {
    persist(PlayEventMapper.toEntity(event));
  }

  @Override
  public Optional<Instant> lastPlayAt(AccountId account, TrackId track) {
    List<PlayEventEntity> rows =
        find(
                "accountId = ?1 and trackId = ?2",
                Sort.descending("at"),
                account.value(),
                track.value())
            .range(0, 0)
            .list();
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0).at);
  }
}
