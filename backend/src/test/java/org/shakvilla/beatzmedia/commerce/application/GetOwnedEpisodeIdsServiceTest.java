package org.shakvilla.beatzmedia.commerce.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.commerce.application.service.GetOwnedEpisodeIdsService;
import org.shakvilla.beatzmedia.commerce.domain.OrderId;
import org.shakvilla.beatzmedia.commerce.domain.OwnershipGrant;
import org.shakvilla.beatzmedia.commerce.fakes.FakeOwnershipRepository;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Unit tests for {@link GetOwnedEpisodeIdsService} — the commerce input port consumed by the
 * podcasts module's {@code OwnershipReader} adapter (WU-POD-1). Proves episode ownership is read
 * only from real {@code ownership_grant} rows (via the fake), never assumed.
 */
@Tag("unit")
class GetOwnedEpisodeIdsServiceTest {

  private static final AccountId ACCOUNT = new AccountId("acct-1");
  private static final OrderId ORDER = new OrderId("order-1");

  FakeOwnershipRepository repository;
  GetOwnedEpisodeIdsService service;

  @BeforeEach
  void setUp() {
    repository = new FakeOwnershipRepository();
    service = new GetOwnedEpisodeIdsService(repository);
  }

  @Test
  void isOwned_activeGrant_returnsTrue() {
    repository.save(
        OwnershipGrant.forEpisode("g1", ACCOUNT, "ep-1", ORDER, Instant.parse("2026-06-01T00:00:00Z")));

    assertTrue(service.isOwned(ACCOUNT, "ep-1"));
  }

  @Test
  void isOwned_noGrant_returnsFalse() {
    assertFalse(service.isOwned(ACCOUNT, "ep-unowned"));
  }

  @Test
  void ownedOf_returnsOnlyOwnedCandidates_batched() {
    repository.save(
        OwnershipGrant.forEpisode("g1", ACCOUNT, "ep-1", ORDER, Instant.parse("2026-06-01T00:00:00Z")));
    repository.save(
        OwnershipGrant.forEpisode("g2", ACCOUNT, "ep-2", ORDER, Instant.parse("2026-06-01T00:00:00Z")));

    Set<String> owned = service.ownedOf(ACCOUNT, List.of("ep-1", "ep-2", "ep-3"));

    assertEquals(Set.of("ep-1", "ep-2"), owned);
  }

  @Test
  void ownedOf_emptyCandidates_returnsEmptySet() {
    assertEquals(Set.of(), service.ownedOf(ACCOUNT, List.of()));
  }
}
