package org.shakvilla.beatzmedia.commerce.fakes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.shakvilla.beatzmedia.commerce.application.port.out.SaleLedgerPoster;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.payments.application.port.out.DuplicatePostingException;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * In-memory fake {@link SaleLedgerPoster} recording each posted sale split so tests can assert the
 * creator credited (INV-4) and that a settlement posts each creator's split exactly once.
 *
 * <p><strong>Enforces {@code (refType="intent", refId)} uniqueness (finding F1).</strong> The real
 * payments {@code ledger_posting} PK rejects a second posting for the same source ref with a 23505 →
 * {@link DuplicatePostingException}. This fake models that: a duplicate {@code refId} throws
 * {@code DuplicatePostingException}, so a unit test would catch the ORIGINAL F1 bug (all lines posting
 * with the same {@code paymentIntentId}) — the two-creator case would throw on the second creator.
 * With the fix (per-creator-unique {@code refId = intentId:creatorId}) distinct creators never collide.
 */
public class FakeSaleLedgerPoster implements SaleLedgerPoster {

  private final List<Posting> postings = new ArrayList<>();
  private final Set<String> claimedRefs = new HashSet<>(); // models ledger_posting PK on (intent, refId)

  public record Posting(String provider, String creator, long grossMinor, String refId) {}

  @Override
  public void postSaleSplit(String provider, AccountId creator, Money gross, String refId) {
    // (refType is always "intent" for a sale split; the ref must be unique per posting.)
    if (!claimedRefs.add("intent:" + refId)) {
      throw new DuplicatePostingException("intent", refId, null);
    }
    postings.add(new Posting(provider, creator.value(), gross.minor(), refId));
  }

  public List<Posting> postings() {
    return List.copyOf(postings);
  }

  public int count() {
    return postings.size();
  }
}
