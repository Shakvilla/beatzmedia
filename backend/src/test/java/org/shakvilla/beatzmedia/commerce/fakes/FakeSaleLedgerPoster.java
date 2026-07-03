package org.shakvilla.beatzmedia.commerce.fakes;

import java.util.ArrayList;
import java.util.List;

import org.shakvilla.beatzmedia.commerce.application.port.out.SaleLedgerPoster;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * In-memory fake {@link SaleLedgerPoster} recording each posted sale split so tests can assert the
 * creator credited (INV-4) and that a re-delivered settlement posts the split exactly once.
 */
public class FakeSaleLedgerPoster implements SaleLedgerPoster {

  private final List<Posting> postings = new ArrayList<>();

  public record Posting(String provider, String creator, long grossMinor, String refId) {}

  @Override
  public void postSaleSplit(String provider, AccountId creator, Money gross, String refId) {
    postings.add(new Posting(provider, creator.value(), gross.minor(), refId));
  }

  public List<Posting> postings() {
    return List.copyOf(postings);
  }

  public int count() {
    return postings.size();
  }
}
