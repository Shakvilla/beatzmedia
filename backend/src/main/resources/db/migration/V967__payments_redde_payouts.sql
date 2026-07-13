-- WU-PAY-7: Real payout disbursement via Redde /v1/cashout (LLFR-PAYMENTS-07.1 .. 07.2).
-- Payments ADD §7. Forward-only; never edits V701..V704 / V966.
--
-- This flips disbursement from synchronous-optimistic (WU-PAY-4: post the ledger clearing + mark PAID
-- the instant an admin triggers a run) to ASYNC-CONFIRMED, mirroring the inbound-charge lifecycle:
--   1. admin trigger  -> gateway.disburse() sends the cashout -> withdrawal 'sent', payout_txn 'sent',
--                        NO ledger disbursement posting yet
--   2. cashout webhook -> pull-back-verified PAID  -> post the disbursement clearing + mark 'paid'
--                                          FAILED  -> mark 'failed' + AuditEntry, no ledger posting
--   3. PayoutReconJob (30s) polls 'sent' withdrawals whose webhook never arrived.
-- The ledger disbursement posting therefore MOVES from send-time to confirmed-settlement time, so a
-- cashout that later fails can never leave the ledger claiming money that never left (INV-6).
--
-- Structured payout destinations replace the opaque free-text payout_method.detail for the gateway
-- call: Redde needs a network+wallet (momo) or a bank code + account (bank). detail stays as a legacy
-- display label. Columns are nullable/additive so existing rows are untouched.

-- ---------------------------------------------------------------------------
-- payout_method — structured destination fields alongside the legacy label/detail.
--   momo: network (mtn/telecel/airteltigo) + wallet_number
--   bank: bank_name + bank_code + account_name + account_number
-- Validation (momo requires network+wallet; bank requires all four) is enforced in the domain, not by
-- a CHECK, because the four bank columns and the two momo columns are mutually exclusive by kind and a
-- single-row CHECK spanning kind would be brittle across future kinds. network is CHECK-guarded since
-- it is a closed rail set.
-- ---------------------------------------------------------------------------
ALTER TABLE payout_method
    ADD COLUMN network        TEXT CHECK (network IN ('mtn','telecel','airteltigo')),
    ADD COLUMN wallet_number  TEXT,
    ADD COLUMN bank_name      TEXT,
    ADD COLUMN bank_code      TEXT,
    ADD COLUMN account_name   TEXT,
    ADD COLUMN account_number TEXT;

-- ---------------------------------------------------------------------------
-- withdrawal_request — add the in-flight 'sent' state between the admin trigger and confirmed 'paid'.
-- Transitions: pending/ready -> sent -> paid | failed.
-- ---------------------------------------------------------------------------
ALTER TABLE withdrawal_request DROP CONSTRAINT withdrawal_request_status_check;
ALTER TABLE withdrawal_request ADD CONSTRAINT withdrawal_request_status_check
    CHECK (status IN ('pending','ready','sent','paid','failed'));

-- ---------------------------------------------------------------------------
-- payout_txn — a txn is now inserted at SEND time (status 'sent') before the ledger clears, so:
--   * disburse_txn_id becomes nullable (the ledger trace id is only known at confirmed-settlement)
--   * status gains a CHECK over the async lifecycle ('sent' -> 'paid' | 'failed')
-- The uq_payout_per_withdrawal UNIQUE (withdrawal_id) exactly-once guard is unchanged and still the
-- durable no-double-send backstop.
-- ---------------------------------------------------------------------------
ALTER TABLE payout_txn ALTER COLUMN disburse_txn_id DROP NOT NULL;
ALTER TABLE payout_txn ADD CONSTRAINT payout_txn_status_check
    CHECK (status IN ('sent','paid','failed'));

-- ---------------------------------------------------------------------------
-- payout_event — idempotency backstop for HandleCashoutWebhookService, the payout analog of
-- payment_event (V702). provider_event_id is UNIQUE so a duplicate/replayed cashout callback applies
-- the settlement transition at most once (INSERT ... ON CONFLICT DO NOTHING). Kept SEPARATE from
-- payment_event so a cashout confirmation can never be mistaken for or collide with a charge
-- confirmation (Redde's two callback payloads are structurally identical — disambiguation is by
-- webhook PATH, not payload inspection).
-- ---------------------------------------------------------------------------
CREATE TABLE payout_event (
    id                TEXT PRIMARY KEY,
    withdrawal_id     TEXT NOT NULL REFERENCES withdrawal_request(id),
    provider_event_id TEXT NOT NULL,
    type              TEXT NOT NULL CHECK (type IN ('SETTLED','FAILED','PENDING')),
    reason            TEXT,
    received_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payout_event_provider UNIQUE (provider_event_id)
);

-- Cashout webhook handling resolves/records events per withdrawal; index the FK for those lookups.
CREATE INDEX idx_payout_event_withdrawal ON payout_event (withdrawal_id);
