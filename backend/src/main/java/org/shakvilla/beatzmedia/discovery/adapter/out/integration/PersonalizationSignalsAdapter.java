package org.shakvilla.beatzmedia.discovery.adapter.out.integration;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.catalog.application.port.in.ResolveCatalog;
import org.shakvilla.beatzmedia.discovery.application.port.out.PersonalizationSignals;
import org.shakvilla.beatzmedia.engagement.application.port.in.EngagementHistory;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.application.port.in.CollectionView;
import org.shakvilla.beatzmedia.library.application.port.in.GetCollection;

@ApplicationScoped
public class PersonalizationSignalsAdapter implements PersonalizationSignals {

  private static final Logger LOG = Logger.getLogger(PersonalizationSignalsAdapter.class);
  private static final int HISTORY_LIMIT = 50;

  private final GetCollection collections;
  private final ResolveCatalog catalog;
  private final EngagementHistory engagement;

  @Inject
  public PersonalizationSignalsAdapter(
      GetCollection collections, ResolveCatalog catalog, EngagementHistory engagement) {
    this.collections = collections;
    this.catalog = catalog;
    this.engagement = engagement;
  }

  @Override
  public Snapshot load(String accountId) {
    CollectionView collection = collection(accountId);
    History history = history(accountId);

    var trackIds = new java.util.LinkedHashSet<String>();
    trackIds.addAll(collection.likedTracks());
    history.items().stream()
        .filter(item -> "track".equals(item.contentKind()))
        .map(EngagementHistory.HistoryItem::contentId)
        .forEach(trackIds::add);

    Set<String> preferredCreators = new HashSet<>(collection.followedArtists());
    Set<String> preferredTags = new HashSet<>();
    try {
      var resolved =
          catalog.resolve(
              new ResolveCatalog.Command(
                  trackIds.stream().limit(100).toList(),
                  collection.followedArtists().stream().limit(50).toList(),
                  collection.savedAlbums().stream().limit(50).toList(),
                  List.of()),
              Optional.of(accountId));
      resolved.tracks().forEach(track -> preferredCreators.add(track.artistId()));
      resolved.albums().forEach(
          album -> {
            preferredCreators.add(album.artistId());
            album.genres().stream().map(PersonalizationSignalsAdapter::normalize).forEach(preferredTags::add);
          });
      resolved.artists().forEach(
          artist -> artist.genres().stream().map(PersonalizationSignalsAdapter::normalize).forEach(preferredTags::add));
    } catch (RuntimeException failure) {
      LOG.debug("Personalization catalog signals unavailable; using relationship signals", failure);
    }

    Set<String> completed = new HashSet<>();
    Set<String> recent = new HashSet<>();
    for (var item : history.items()) {
      String key = item.contentKind() + ":" + item.contentId();
      recent.add(key);
      if (item.completed()) completed.add(key);
    }
    return new Snapshot(
        Set.copyOf(collection.followedArtists()),
        Set.copyOf(collection.followedShows()),
        Set.copyOf(collection.ownedTracks()),
        completed,
        recent,
        preferredCreators,
        preferredTags,
        history.enabled());
  }

  private CollectionView collection(String accountId) {
    try {
      return collections.get(new AccountId(accountId));
    } catch (RuntimeException failure) {
      LOG.debug("Personalization library signals unavailable; using cold start", failure);
      return new CollectionView(
          List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
  }

  private History history(String accountId) {
    try {
      boolean enabled = engagement.preference(accountId).historyEnabled();
      if (!enabled) return new History(false, List.of());
      return new History(true, engagement.recentlyPlayed(accountId, null, HISTORY_LIMIT).items());
    } catch (RuntimeException failure) {
      LOG.debug("Personalization history unavailable; continuing without history", failure);
      return new History(false, List.of());
    }
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
  }

  private record History(boolean enabled, List<EngagementHistory.HistoryItem> items) {}
}
