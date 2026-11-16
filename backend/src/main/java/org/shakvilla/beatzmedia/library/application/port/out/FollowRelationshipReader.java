package org.shakvilla.beatzmedia.library.application.port.out;

import java.util.List;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.domain.FollowKind;

/** Persistence read port for forward and reverse follow-graph traversal. */
public interface FollowRelationshipReader {

  List<String> followedTargetIds(AccountId accountId, FollowKind kind);

  List<String> followerAccountIds(FollowKind kind, String targetId, String cursor, int limit);
}
