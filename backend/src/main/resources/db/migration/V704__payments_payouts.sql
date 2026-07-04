-- WU-PAY-4: Payout methods + KYC-gated withdrawals + admin payout runs
-- (LLFR-PAYMENTS-03.1 .. 03.4). Payments ADD §7. Fourth migration in the payments band (V7xx).
-- Forward-only; never edits V701/V702/V703.
--
-- Money is integer minor units (pesewas), INV-11. Ids are UUIDv7 strings (TEXT, matching the rest of
-- the codebase — see payment_intent.id in V701 / ledger_account.id in V703). Enum-like columns use
-- TEXT + CHECK for additive evolution (consistent with V701..V703), NOT the PG enum type the ADD's
-- illustrative DDL sketched.
--
-- MONEY-SAFETY OVERVIEW (reviewers: read this):
--   * A withdrawal RESERVES funds at request time by posting a balanced, already-cleared ledger txn:
--       DEBIT creator_payable  (amount)   -- reduces the creator's available balance NOW
--       CREDIT payout_clearing (amount)   -- funds in-flight for a cash-out
--     so a second concurrent withdrawal sees the reduced balance and cannot double-spend (INV-6/INV-8).
--   * Executing a payout (weekly run / single send) posts the disbursement clearing:
--       DEBIT payout_clearing (amount)    -- funds leave the clearing account
--       CREDIT provider_clearing (amount) -- paid out via the rail
--     guarded by a UNIQUE (withdrawal_id) on payout_txn so a retried run can NEVER double-pay (INV-6).

-- ---------------------------------------------------------------------------
-- kyc_record — the creator's KYC verification state (INV-8). Payments-owned mirror of the identity
-- KYC decision, read via the KycProvider port. A withdrawal is gated on status = 'verified'.
-- (The ADD models KycProvider as "resolves to identity", but identity has no KYC surface yet, so the
--  authoritative record lives here in the payments band per the ADD §7 schema — additive; a future
--  identity KYC WU can back the KycProvider port with an identity table without changing this module.)
-- ---------------------------------------------------------------------------
CREATE TABLE kyc_record (
    account_id  TEXT PRIMARY KEY,
    status      TEXT NOT NULL DEFAULT 'none'
                    CHECK (status IN ('none','pending','verified','rejected')),
    verified_at TIMESTAMPTZ,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- payout_method — a creator's cash-out destination (MoMo or bank). Exactly one default per account.
-- ---------------------------------------------------------------------------
CREATE TABLE payout_method (
    id         TEXT PRIMARY KEY,
    account_id TEXT NOT NULL,
    kind       TEXT NOT NULL CHECK (kind IN ('momo','bank')),
    label      TEXT NOT NULL,
    detail     TEXT NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- At most one default method per account (partial unique index).
CREATE UNIQUE INDEX uq_payout_method_default ON payout_method (account_id) WHERE is_default;
CREATE INDEX ix_payout_method_account ON payout_method (account_id);

-- ---------------------------------------------------------------------------
-- withdrawal_request — a creator's cash-out request. KYC- and floor-gated (INV-8). amount_minor is the
-- gross the creator draws from available balance; fee_minor is the rail fee (informational, from
-- PlatformSettings/withdrawalFee). idempotency_key UNIQUE makes a duplicate request a no-op replay.
-- Statuses:
--   pending — reserved, awaiting a payout run (funds already debited from available on request)
--   ready   — synonym for pending here (a KYC-verified reserved request eligible for a run)
--   paid    — executed by a payout run; payout_txn exists
--   failed  — the reservation was reversed (out of scope to auto-trigger at this WU)
-- The CHECK amount_minor >= 1000 is the DB backstop for the ₵10 floor (INV-8); the config-driven
-- minimum (PlatformSettings.payoutMinimumMinor) is enforced in-app and may be RAISED above ₵10.
-- ---------------------------------------------------------------------------
CREATE TABLE withdrawal_request (
    id              TEXT PRIMARY KEY,
    account_id      TEXT NOT NULL,
    amount_minor    BIGINT NOT NULL CHECK (amount_minor >= 1000),  -- ≥ ₵10 floor backstop (INV-8)
    fee_minor       BIGINT NOT NULL CHECK (fee_minor >= 0),
    method_id       TEXT NOT NULL REFERENCES payout_method(id),
    status          TEXT NOT NULL DEFAULT 'pending'
                        CHECK (status IN ('pending','ready','paid','failed')),
    reserve_txn_id  TEXT NOT NULL,   -- the ledger txn that reserved the funds (trace)
    idempotency_key TEXT NOT NULL,
    requested_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_withdrawal_idem UNIQUE (idempotency_key)
);
CREATE INDEX ix_withdrawal_account ON withdrawal_request (account_id);
CREATE INDEX ix_withdrawal_status ON withdrawal_request (status);
CREATE INDEX ix_withdrawal_method ON withdrawal_request (method_id);

-- ---------------------------------------------------------------------------
-- payout_batch — one row per admin payout run (weekly batch or single send). Groups the paid txns.
-- ---------------------------------------------------------------------------
CREATE TABLE payout_batch (
    id          TEXT PRIMARY KEY,
    kind        TEXT NOT NULL CHECK (kind IN ('weekly','single')),
    run_by      TEXT NOT NULL,   -- admin account id (INV-10 actor)
    status      TEXT NOT NULL DEFAULT 'completed',
    total_minor BIGINT NOT NULL DEFAULT 0,
    count       INT NOT NULL DEFAULT 0,
    run_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- payout_txn — one executed disbursement per withdrawal. The UNIQUE (withdrawal_id) is the durable
-- EXACTLY-ONCE guard: a re-run / retried single-send for the same withdrawal fails on this constraint,
-- so a withdrawal can NEVER be paid twice (no double-pay, INV-6). Reviewers: this is the payout analog
-- of the ledger_posting exactly-once header (WU-PAY-3 finding F1).
-- ---------------------------------------------------------------------------
CREATE TABLE payout_txn (
    id            TEXT PRIMARY KEY,
    batch_id      TEXT NOT NULL REFERENCES payout_batch(id),
    withdrawal_id TEXT NOT NULL REFERENCES withdrawal_request(id),
    account_id    TEXT NOT NULL,
    amount_minor  BIGINT NOT NULL CHECK (amount_minor > 0),
    status        TEXT NOT NULL DEFAULT 'paid',
    provider_ref  TEXT,
    disburse_txn_id TEXT NOT NULL,   -- the ledger txn that cleared the disbursement (trace)
    paid_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payout_per_withdrawal UNIQUE (withdrawal_id)
);
CREATE INDEX ix_payout_txn_batch ON payout_txn (batch_id);
CREATE INDEX ix_payout_txn_account ON payout_txn (account_id);
