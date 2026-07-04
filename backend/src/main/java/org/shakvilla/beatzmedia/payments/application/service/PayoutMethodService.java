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
import org.shakvilla.beatzmedia.payments.domain.MethodKind;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethod;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethodId;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethodNotFoundException;
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

    // The first method a creator adds becomes their default.
    boolean makeDefault = !repository.hasAnyMethod(creator);
    if (makeDefault) {
      repository.clearDefaultMethods(creator);
    }
    PayoutMethod method =
        PayoutMethod.create(
            ids.newId(),
            creator,
            cmd.kind(),
            cmd.label().trim(),
            cmd.detail().trim(),
            makeDefault,
            clock.now());
    repository.saveMethod(method);
    audit(creator, "ADD_PAYOUT_METHOD", method.getId().value());
    return PayoutMethodView.of(method);
  }

  @Override
  @Transactional
  public void remove(AccountId creator, PayoutMethodId id) {
    PayoutMethod method =
        repository.findMethod(creator, id).orElseThrow(() -> new PayoutMethodNotFoundException(id));
    repository.deleteMethod(creator, method.getId());
    audit(creator, "REMOVE_PAYOUT_METHOD", id.value());
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
