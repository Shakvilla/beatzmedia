package org.shakvilla.beatzmedia.library.application.port.in;

import java.util.List;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.domain.FollowKind;

/** Narrow cross-module read of the targets followed by one account. */
public interface GetFollowedTargets {

  List<String> get(AccountId accountId, FollowKind kind);
}
