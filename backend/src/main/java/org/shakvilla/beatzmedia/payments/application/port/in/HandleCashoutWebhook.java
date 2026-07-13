package org.shakvilla.beatzmedia.payments.application.port.in;

/**
 * Inbound port for Redde "Cash Out Callback URL" notifications (WU-PAY-7, ADR-28). Redde posts an
 * unsigned callback when a {@code /v1/cashout} disbursement progresses; we never trust its body.
 * The handler extracts the {@code transactionid}, confirms the outcome by an authenticated pull-back
 * ({@code GET /v1/status}), and only then posts the disbursement ledger txn + marks the withdrawal
 * paid (or marks it failed) — so the ledger never claims money that never left (INV-6).
 *
 * <p>Separate from {@link HandleReddeReceipt} (the charge-side "Receive Callback URL"): Redde's two
 * callback payloads are structurally identical (no type discriminator), so a charge confirmation and
 * a cashout confirmation are disambiguated by <strong>path</strong>, not payload inspection, and each
 * has its own idempotency table so one can never be mistaken for the other.
 */
public interface HandleCashoutWebhook {

  /** Handle a raw Redde cashout callback. See {@link WebhookResult} for the HTTP mapping. */
  WebhookResult handle(byte[] rawBody);
}
