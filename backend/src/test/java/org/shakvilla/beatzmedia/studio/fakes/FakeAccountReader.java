package org.shakvilla.beatzmedia.studio.fakes;

import org.shakvilla.beatzmedia.studio.application.port.out.AccountReader;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;

/** In-memory fake for {@link AccountReader} used in unit tests. */
public class FakeAccountReader implements AccountReader {

  private final String email;

  public FakeAccountReader(String email) {
    this.email = email;
  }

  public static FakeAccountReader of(String email) {
    return new FakeAccountReader(email);
  }

  @Override
  public String emailOf(ArtistId artist) {
    return email;
  }
}
