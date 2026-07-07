package org.shakvilla.beatzmedia.admin.fakes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.admin.application.port.out.IdentityReader;

/**
 * In-memory fake for {@link IdentityReader}. Testing-strategy §2.
 */
public class FakeIdentityReader implements IdentityReader {

  private final Map<String, String> names = new HashMap<>();

  public void seed(String accountId, String displayName) {
    names.put(accountId, displayName);
  }

  @Override
  public Optional<String> displayNameOf(String accountId) {
    return Optional.ofNullable(names.get(accountId));
  }
}
