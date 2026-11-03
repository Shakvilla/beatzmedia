package org.shakvilla.beatzmedia.library.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.application.port.out.FollowRelationshipReader;
import org.shakvilla.beatzmedia.library.application.service.FollowRelationshipService;
import org.shakvilla.beatzmedia.library.domain.FollowKind;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

@Tag("unit")
class FollowRelationshipServiceTest {

  @Test
  void followerResolutionIsBoundedAndReturnsAResumableCursor() {
    FollowRelationshipReader reader =
        new FollowRelationshipReader() {
          @Override
          public List<String> followedTargetIds(AccountId accountId, FollowKind kind) {
            return List.of("artist-1");
          }

          @Override
          public List<String> followerAccountIds(
              FollowKind kind, String targetId, String cursor, int limit) {
            return List.of("account-1", "account-2", "account-3");
          }
        };
    var service = new FollowRelationshipService(reader);

    var page = service.resolve(FollowKind.artist, "artist-1", null, 2);

    assertEquals(List.of("account-1", "account-2"), page.accountIds());
    assertEquals("account-2", page.nextCursor());
    assertEquals(List.of("artist-1"), service.get(new AccountId("account-1"), FollowKind.artist));
  }

  @Test
  void playlistFollowersAreOutsideThePublicationFanoutBoundary() {
    var service =
        new FollowRelationshipService(
            new FollowRelationshipReader() {
              @Override
              public List<String> followedTargetIds(AccountId accountId, FollowKind kind) {
                return List.of();
              }

              @Override
              public List<String> followerAccountIds(
                  FollowKind kind, String targetId, String cursor, int limit) {
                return List.of();
              }
            });

    assertThrows(
        ValidationException.class,
        () -> service.resolve(FollowKind.playlist, "playlist-1", null, 20));
  }
}
