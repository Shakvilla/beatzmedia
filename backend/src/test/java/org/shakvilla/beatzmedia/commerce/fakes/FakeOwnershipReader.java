package org.shakvilla.beatzmedia.commerce.fakes;

import java.util.HashSet;
import java.util.Set;

import org.shakvilla.beatzmedia.commerce.application.port.out.OwnershipReader;
import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/** In-memory fake OwnershipReader for unit tests. Seed with owns(account, kind, refId). */
public class FakeOwnershipReader implements OwnershipReader {

  private final Set<String> owned = new HashSet<>();

  public void markOwned(AccountId account, CartItemKind kind, String refId) {
    owned.add(key(account, kind, refId));
  }

  @Override
  public boolean isOwned(AccountId account, CartItemKind kind, String refId) {
    return owned.contains(key(account, kind, refId));
  }

  private String key(AccountId account, CartItemKind kind, String refId) {
    return account.value() + ":" + kind.wireValue() + ":" + refId;
  }
}
