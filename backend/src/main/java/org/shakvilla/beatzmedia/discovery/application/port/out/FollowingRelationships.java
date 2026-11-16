package org.shakvilla.beatzmedia.discovery.application.port.out;

import java.util.List;

public interface FollowingRelationships {

  List<String> followedArtistIds(String accountId);

  List<String> followedShowIds(String accountId);
}
