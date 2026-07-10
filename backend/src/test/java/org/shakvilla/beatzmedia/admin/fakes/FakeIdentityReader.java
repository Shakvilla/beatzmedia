package org.shakvilla.beatzmedia.admin.fakes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.admin.application.port.out.IdentityReader;

/**
 * In-memory fake for {@link IdentityReader}. Testing-strategy §2.
 */
public class FakeIdentityReader implements IdentityReader {

  private final Map<String, String> names = new HashMap<>();
  private final List<Instant> newArtistCreatedAt = new ArrayList<>();
  private int activeAccounts;

  public void seed(String accountId, String displayName) {
    names.put(accountId, displayName);
  }

  /** Seeds the count returned by {@link #countActiveAccounts()}. */
  public void seedActiveAccounts(int count) {
    this.activeAccounts = count;
  }

  /** Seeds one artist account's {@code created_at}, counted by {@link #countNewArtists(Instant)}. */
  public void seedNewArtist(Instant createdAt) {
    newArtistCreatedAt.add(createdAt);
  }

  @Override
  public Optional<String> displayNameOf(String accountId) {
    return Optional.ofNullable(names.get(accountId));
  }

  @Override
  public int countActiveAccounts() {
    return activeAccounts;
  }

  @Override
  public int countNewArtists(Instant since) {
    return (int) newArtistCreatedAt.stream().filter(t -> !t.isBefore(since)).count();
  }
}
