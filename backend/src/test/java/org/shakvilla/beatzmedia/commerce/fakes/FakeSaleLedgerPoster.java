package org.shakvilla.beatzmedia.commerce.fakes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.shakvilla.beatzmedia.commerce.application.port.out.SaleLedgerPoster;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * In-memory fake {@link SaleLedgerPoster} recording each committed sale split so tests can assert the
 * creator credited (INV-4) and that a settlement posts each creator's split exactly once.
 *
 * <p><strong>Models the port contract precisely, including the adapter's duplicate-swallow.</strong>
 * The real payments {@code ledger_posting} PK rejects a second posting for the same {@code (intent,
 * refId)} with a 23505 → {@code DuplicatePostingException}; the real {@code
 * PaymentsSaleLedgerPosterAdapter} <em>swallows</em> that as a benign idempotent no-op (its
 * {@code REQUIRES_NEW} transaction rolls back in isolation). This fake mirrors both halves: a duplicate
 * {@code (intent, refId)} is <strong>recorded once and silently ignored thereafter</strong> — it never
 * propagates to the caller (matching the adapter). So the ORIGINAL F1 bug (all lines posting with the
 * same {@code paymentIntentId}) surfaces as a MISSING posting (only one split recorded, distinct-ref
 * assertions fail), and a legitimate retry that re-posts an already-committed creator (finding B) is a
 * benign no-op — no double credit, no exception.
 */
public class FakeSaleLedgerPoster implements SaleLedgerPoster {

  private final List<Posting> postings = new ArrayList<>();
  private final Set<String> claimedRefs = new HashSet<>(); // models ledger_posting PK on (intent, refId)
  private final Set<String> failOnceForCreator = new HashSet<>(); // creators to fail ONCE (transient)

  public record Posting(String provider, String creator, long grossMinor, String refId) {}

  /**
   * Arrange a NON-duplicate transient failure the FIRST time this creator is posted (models e.g. a
   * transient DB error / UnbalancedLedgerException on delivery 1). The claim is NOT recorded, so a
   * retry succeeds — mirroring the real {@code REQUIRES_NEW} split whose failing transaction rolls back
   * its own claim insert (finding B / atomicity). This is a genuinely-propagating error (unlike a
   * duplicate, which the adapter swallows).
   */
  public void failOnceForCreator(String creatorId) {
    failOnceForCreator.add(creatorId);
  }

  @Override
  public void postSaleSplit(String provider, AccountId creator, Money gross, String refId) {
    if (failOnceForCreator.remove(creator.value())) {
      // Transient non-duplicate failure: nothing is claimed/recorded (the real REQUIRES_NEW txn would
      // roll back, undoing its own claim). A retry re-attempts and succeeds. This one DOES propagate.
      throw new IllegalStateException("transient ledger failure for creator " + creator.value());
    }
    // Duplicate (intent, refId): the real adapter swallows DuplicatePostingException as a benign
    // no-op, so the caller never sees it — model that by recording once and silently ignoring repeats.
    if (!claimedRefs.add("intent:" + refId)) {
      return;
    }
    postings.add(new Posting(provider, creator.value(), gross.minor(), refId));
  }

  public List<Posting> postings() {
    return List.copyOf(postings);
  }

  public int count() {
    return postings.size();
  }

  /** How many committed splits credit the given creator (should be exactly 1 in steady state). */
  public long countForCreator(String creatorId) {
    return postings.stream().filter(p -> p.creator().equals(creatorId)).count();
  }
}
