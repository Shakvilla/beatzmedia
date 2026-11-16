package org.shakvilla.beatzmedia.discovery.adapter.out.integration;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.discovery.application.port.out.FollowingRelationships;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.application.port.in.GetFollowedTargets;
import org.shakvilla.beatzmedia.library.domain.FollowKind;

@ApplicationScoped
public class LibraryFollowingRelationshipsAdapter implements FollowingRelationships {

  private final GetFollowedTargets followedTargets;

  @Inject
  public LibraryFollowingRelationshipsAdapter(GetFollowedTargets followedTargets) {
    this.followedTargets = followedTargets;
  }

  @Override
  public List<String> followedArtistIds(String accountId) {
    return followedTargets.get(new AccountId(accountId), FollowKind.artist);
  }

  @Override
  public List<String> followedShowIds(String accountId) {
    return followedTargets.get(new AccountId(accountId), FollowKind.show);
  }
}
