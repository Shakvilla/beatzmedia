package org.shakvilla.beatzmedia.library.application.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.application.port.in.GetFollowedTargets;
import org.shakvilla.beatzmedia.library.application.port.in.ResolveFollowers;
import org.shakvilla.beatzmedia.library.application.port.out.FollowRelationshipReader;
import org.shakvilla.beatzmedia.library.domain.FollowKind;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

@ApplicationScoped
@Transactional(Transactional.TxType.SUPPORTS)
public class FollowRelationshipService implements GetFollowedTargets, ResolveFollowers {

  private final FollowRelationshipReader relationships;

  @Inject
  public FollowRelationshipService(FollowRelationshipReader relationships) {
    this.relationships = relationships;
  }

  @Override
  public List<String> get(AccountId accountId, FollowKind kind) {
    requireSupported(kind);
    return relationships.followedTargetIds(accountId, kind);
  }

  @Override
  public FollowerPage resolve(FollowKind kind, String targetId, String cursor, int limit) {
    requireSupported(kind);
    if (targetId == null || targetId.isBlank()) {
      throw new ValidationException("targetId is required", "targetId");
    }
    int bounded = Math.max(1, Math.min(limit <= 0 ? 100 : limit, 100));
    List<String> rows = relationships.followerAccountIds(kind, targetId, cursor, bounded + 1);
    boolean hasNext = rows.size() > bounded;
    List<String> items = hasNext ? List.copyOf(rows.subList(0, bounded)) : List.copyOf(rows);
    String nextCursor = hasNext ? items.getLast() : null;
    return new FollowerPage(items, nextCursor);
  }

  private static void requireSupported(FollowKind kind) {
    if (kind != FollowKind.artist && kind != FollowKind.show) {
      throw new ValidationException("Follower resolution supports artists and shows", "kind");
    }
  }
}
