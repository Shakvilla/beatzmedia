package org.shakvilla.beatzmedia.library.application.port.in;

import java.util.List;

import org.shakvilla.beatzmedia.library.domain.FollowKind;

/** Stable, keyset-paged follower resolution used by resumable publication fanout jobs. */
public interface ResolveFollowers {

  FollowerPage resolve(FollowKind kind, String targetId, String cursor, int limit);

  record FollowerPage(List<String> accountIds, String nextCursor) {}
}
