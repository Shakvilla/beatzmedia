package org.shakvilla.beatzmedia.admin.fakes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.admin.application.port.out.IdentityReader;
import org.shakvilla.beatzmedia.admin.domain.UserFilter;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * In-memory fake for {@link IdentityReader}. Testing-strategy §2.
 */
public class FakeIdentityReader implements IdentityReader {

  private final Map<String, String> names = new HashMap<>();
  private final List<Instant> newArtistCreatedAt = new ArrayList<>();
  private final Map<String, AccountRow> accounts = new LinkedHashMap<>();
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

  /** Seeds a full account row for {@link #listUsers}/{@link #findUser}/{@link #countUsers}. */
  public void seedAccount(AccountRow row) {
    accounts.put(row.id(), row);
    names.put(row.id(), row.name());
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

  @Override
  public Page<AccountRow> listUsers(String q, UserFilter filter, PageRequest page) {
    List<AccountRow> matched = accounts.values().stream()
        .filter(row -> matchesFilter(row, filter))
        .filter(row -> matchesQuery(row, q))
        .sorted(Comparator.comparing(AccountRow::createdAt).reversed())
        .toList();

    long total = matched.size();
    int from = page.offset();
    int to = Math.min(from + page.size(), matched.size());
    List<AccountRow> items = from >= matched.size() ? List.of() : matched.subList(from, to);
    return Page.of(items, page.page(), page.size(), total);
  }

  @Override
  public Optional<AccountRow> findUser(String accountId) {
    return Optional.ofNullable(accounts.get(accountId));
  }

  @Override
  public UserCounts countUsers() {
    int all = accounts.size();
    int fans = (int) accounts.values().stream().filter(r -> !r.isArtist()).count();
    int artists = (int) accounts.values().stream().filter(AccountRow::isArtist).count();
    int verified = (int) accounts.values().stream().filter(AccountRow::verified).count();
    int suspended = (int) accounts.values().stream().filter(r -> "suspended".equals(r.status())).count();
    return new UserCounts(all, fans, artists, verified, suspended);
  }

  private static boolean matchesFilter(AccountRow row, UserFilter filter) {
    if (filter == null) {
      return true;
    }
    return switch (filter) {
      case FANS -> !row.isArtist();
      case ARTISTS -> row.isArtist();
      case VERIFIED -> row.verified();
      case SUSPENDED -> "suspended".equals(row.status());
    };
  }

  private static boolean matchesQuery(AccountRow row, String q) {
    if (q == null || q.isBlank()) {
      return true;
    }
    String lower = q.toLowerCase(java.util.Locale.ROOT);
    return row.name().toLowerCase(java.util.Locale.ROOT).contains(lower)
        || row.email().toLowerCase(java.util.Locale.ROOT).contains(lower);
  }
}
