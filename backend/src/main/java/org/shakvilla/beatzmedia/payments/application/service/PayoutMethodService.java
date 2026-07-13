package org.shakvilla.beatzmedia.payments.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.payments.application.port.in.AddPayoutMethod;
import org.shakvilla.beatzmedia.payments.application.port.in.PayoutMethodView;
import org.shakvilla.beatzmedia.payments.application.port.in.RemovePayoutMethod;
import org.shakvilla.beatzmedia.payments.application.port.in.SetDefaultPayoutMethod;
import org.shakvilla.beatzmedia.payments.application.port.out.PayoutRepository;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.GhanaBankCode;
import org.shakvilla.beatzmedia.payments.domain.MethodKind;
import org.shakvilla.beatzmedia.payments.domain.PayoutDestination;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethod;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethodId;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethodInUseException;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethodNotFoundException;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * Application service for creator payout-method management (LLFR-PAYMENTS-03.1). Implements {@link
 * AddPayoutMethod}, {@link RemovePayoutMethod}, {@link SetDefaultPayoutMethod}.
 *
 * <p>Ownership-scoped: every operation is constrained to the acting creator's own methods (the repo
 * queries filter by {@code accountId}), so one creator can never touch another's destinations. The
 * "exactly one default per account" invariant is upheld by clearing the prior default in the same
 * transaction before setting a new one (backed by the V704 partial-unique index). Every mutation
 * appends an {@link AuditEntry} (INV-10).
 */
@ApplicationScoped
public class PayoutMethodService
    implements AddPayoutMethod, RemovePayoutMethod, SetDefaultPayoutMethod {

  private final PayoutRepository repository;
  private final IdGenerator ids;
  private final Clock clock;
  private final AuditWriter auditWriter;

  @Inject
  public PayoutMethodService(
      PayoutRepository repository, IdGenerator ids, Clock clock, AuditWriter auditWriter) {
    this.repository = repository;
    this.ids = ids;
    this.clock = clock;
    this.auditWriter = auditWriter;
  }

  @Override
  @Transactional
  public PayoutMethodView add(AccountId creator, Command cmd) {
    if (cmd == null || cmd.kind() == null) {
      throw new ValidationException("payout method kind is required", "kind");
    }
    if (cmd.kind() == MethodKind.card) {
      throw new ValidationException("a card is not a valid payout destination", "kind");
    }
    requireText(cmd.label(), "label");
    requireText(cmd.detail(), "detail");
    PayoutDestination destination = destinationFrom(cmd);

    // The first method a creator adds becomes their default.
    boolean makeDefault = !repository.hasAnyMethod(creator);
    if (makeDefault) {
      repository.clearDefaultMethods(creator);
    }
    PayoutMethod method =
        PayoutMethod.create(
            ids.newId(),
            creator,
            cmd.label().trim(),
            cmd.detail().trim(),
            destination,
            makeDefault,
            clock.now());
    repository.saveMethod(method);
    audit(creator, "ADD_PAYOUT_METHOD", method.getId().value());
    return PayoutMethodView.of(method);
  }

  /**
   * Build a validated {@link PayoutDestination} from the add command, translating the domain's
   * {@link IllegalArgumentException} (blank/unknown structured field) into a mapped {@code 422}. The
   * required subset is per-kind: momo needs a MoMo network + wallet; bank needs a known Ghana bank
   * code + name + account name/number.
   */
  private static PayoutDestination destinationFrom(Command cmd) {
    try {
      return switch (cmd.kind()) {
        case momo -> {
          requireText(cmd.network(), "network");
          requireText(cmd.walletNumber(), "walletNumber");
          Provider network = Provider.fromWire(cmd.network().trim());
          if (!network.isMomo()) {
            throw new ValidationException(
                "network must be a MoMo rail (mtn/telecel/airteltigo)", "network");
          }
          yield new PayoutDestination.Momo(network, cmd.walletNumber().trim());
        }
        case bank -> {
          requireText(cmd.bankCode(), "bankCode");
          requireText(cmd.bankName(), "bankName");
          requireText(cmd.accountName(), "accountName");
          requireText(cmd.accountNumber(), "accountNumber");
          yield new PayoutDestination.Bank(
              GhanaBankCode.of(cmd.bankCode().trim()),
              cmd.bankName().trim(),
              cmd.accountName().trim(),
              cmd.accountNumber().trim());
        }
        case card -> throw new ValidationException("a card is not a valid payout destination", "kind");
      };
    } catch (IllegalArgumentException e) {
      // Provider.fromWire / GhanaBankCode.of / PayoutDestination record ctors reject unknown or blank
      // structured fields — surface as a clean 422 rather than a 500.
      throw new ValidationException(e.getMessage(), "destination");
    }
  }

  @Override
  @Transactional
  public void remove(AccountId creator, PayoutMethodId id) {
    PayoutMethod method =
        repository.findMethod(creator, id).orElseThrow(() -> new PayoutMethodNotFoundException(id));
    // Guard: a method referenced by ANY withdrawal (even an old paid one) cannot be deleted — the
    // withdrawal_request.method_id FK is ON DELETE RESTRICT (V704), so a raw delete would surface an
    // opaque FK-violation 500. Pre-check for a deterministic, clean 409 (F-NEW-1).
    if (repository.existsWithdrawalForMethod(creator, method.getId())) {
      throw new PayoutMethodInUseException(id);
    }
    try {
      repository.deleteMethod(creator, method.getId());
    } catch (jakarta.persistence.PersistenceException e) {
      // Backstop: a withdrawal created concurrently after the pre-check trips the FK — translate the
      // constraint violation to the same mapped 409 rather than leaking a 500.
      if (isForeignKeyViolation(e)) {
        throw new PayoutMethodInUseException(id);
      }
      throw e;
    }
    audit(creator, "REMOVE_PAYOUT_METHOD", id.value());
  }

  /** True if the throwable chain is a Postgres FK violation (SQLState 23503). */
  private static boolean isForeignKeyViolation(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof java.sql.SQLException sql && "23503".equals(sql.getSQLState())) {
        return true;
      }
      if (t instanceof org.hibernate.exception.ConstraintViolationException) {
        return true;
      }
    }
    return false;
  }

  @Override
  @Transactional
  public PayoutMethodView setDefault(AccountId creator, PayoutMethodId id) {
    PayoutMethod method =
        repository.findMethod(creator, id).orElseThrow(() -> new PayoutMethodNotFoundException(id));
    repository.clearDefaultMethods(creator);
    method.makeDefault();
    repository.saveMethod(method);
    audit(creator, "SET_DEFAULT_PAYOUT_METHOD", id.value());
    return PayoutMethodView.of(method);
  }

  private void audit(AccountId actor, String action, String targetId) {
    auditWriter.append(
        new AuditEntry(
            ids.newId(),
            actor.value(),
            action,
            "PayoutMethod",
            targetId,
            AuditType.FINANCE,
            null,
            clock.now()));
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new ValidationException(field + " is required", field);
    }
  }
}
