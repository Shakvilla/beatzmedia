-- WU-PAY-5: Refunds / chargebacks / disputes + ownership revocation + clawback
-- (LLFR-PAYMENTS-04.1 .. 04.3). Payments ADD §7. Fifth migration in the payments band (V7xx).
-- Forward-only; never edits V701..V704.
--
-- Money is integer minor units (pesewas), INV-11. Ids are UUIDv7 strings (TEXT, matching the rest of
-- the codebase — see payment_intent.id in V701 / ledger_account.id in V703). Enum-like columns use
-- TEXT + CHECK for additive evolution (consistent with V701..V704), NOT the PG enum type the ADD's
-- illustrative DDL sketched.
--
-- MONEY-SAFETY OVERVIEW (reviewers: read this):
--   A completed refund (INV-9) does TWO balanced things, both exactly-once:
--     1. LEDGER CLAWBACK — reverses the ORIGINAL settled sale/tip split under a NEW txn_id:
--          CREDIT provider_clearing (gross)      -- funds returned to the rail/buyer clearing
--          DEBIT  creator_payable  (creator share) -- claws back the credited share; drives the
--                                                     creator's available NEGATIVE if already withdrawn
--          DEBIT  platform_revenue (platform fee)   -- reverses the fee take
--        Σ DEBIT = Σ CREDIT (INV-6) — enforced by the V703 assert_txn_balanced trigger.
--        The reversal is keyed exactly-once on ('refund', <refund_id>) via the V703 ledger_posting
--        header, so a re-delivered refund/chargeback event can NEVER double-clawback.
--     2. OWNERSHIP REVOCATION — payments emits OrderRefunded (AFTER_SUCCESS); commerce revokes the
--        ownership_grant rows for the order (album/season → ALL constituent tracks/episodes). Payments
--        NEVER writes commerce's tables — the revocation crosses the module boundary as an event.

-- ---------------------------------------------------------------------------
-- dispute — a dispute opened against a settled order (payments ADD §3, HLFR-PAYMENTS-04). Opened by a
-- fan refund request OR by a provider chargeback delivered through the signature-verified webhook
-- (WU-PAY-2). payment_intent_id is the settled intent the clawback reverses (INV-9). is_chargeback
-- marks a provider chargeback (its LOST outcome forces a refund path; WON → reject path).
-- Lifecycle: open → refunded | rejected | escalated (payments ADD §8 state machine).
-- ---------------------------------------------------------------------------
CREATE TABLE dispute (
    id                TEXT PRIMARY KEY,
    order_ref         TEXT NOT NULL,
    payment_intent_id TEXT NOT NULL REFERENCES payment_intent(id),
    kind              TEXT NOT NULL,
    subject           TEXT NOT NULL DEFAULT '',
    detail            TEXT NOT NULL DEFAULT '',
    amount_minor      BIGINT NOT NULL CHECK (amount_minor >= 0),
    is_chargeback     BOOLEAN NOT NULL DEFAULT false,
    status            TEXT NOT NULL DEFAULT 'open'
                          CHECK (status IN ('open','refunded','rejected','escalated')),
    -- Provider chargeback idempotency: a chargeback event carries a provider case id; the first
    -- delivery inserts the dispute, a re-delivery collides on this UNIQUE and is a benign no-op. NULL
    -- for admin-initiated disputes (no provider case), so the partial unique index only guards the
    -- provider-driven path.
    provider_case_id  TEXT,
    opened_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_dispute_order_ref ON dispute (order_ref);
CREATE INDEX ix_dispute_intent ON dispute (payment_intent_id);
CREATE INDEX ix_dispute_status ON dispute (status);
CREATE UNIQUE INDEX uq_dispute_provider_case
    ON dispute (provider_case_id)
    WHERE provider_case_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- dispute_event — the dispute's timeline (payments ADD §3). One row per lifecycle event (opened,
-- refunded, rejected, escalated, chargeback notice), with the acting actor for the audit trail.
-- ---------------------------------------------------------------------------
CREATE TABLE dispute_event (
    id         TEXT PRIMARY KEY,
    dispute_id TEXT NOT NULL REFERENCES dispute(id),
    text       TEXT NOT NULL,
    actor      TEXT,   -- admin account id, or 'provider'/'system' for automated events
    at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_dispute_event_dispute ON dispute_event (dispute_id, at);

-- ---------------------------------------------------------------------------
-- refund — a completed refund of a dispute (payments ADD §3, INV-9). One row per adjudicated refund;
-- its existence records that the ledger clawback was posted and OrderRefunded emitted. The UNIQUE
-- (dispute_id) is a durable one-refund-per-dispute guard (a dispute is refunded at most once), on top
-- of the in-app open→refunded transition guard and the ledger_posting exactly-once clawback claim.
-- ---------------------------------------------------------------------------
CREATE TABLE refund (
    id                TEXT PRIMARY KEY,
    dispute_id        TEXT NOT NULL REFERENCES dispute(id),
    payment_intent_id TEXT NOT NULL,
    amount_minor      BIGINT NOT NULL CHECK (amount_minor > 0),
    reason            TEXT,
    clawback_txn_id   TEXT NOT NULL,   -- the ledger txn that reversed the split (trace)
    at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_refund_per_dispute UNIQUE (dispute_id)
);
CREATE INDEX ix_refund_intent ON refund (payment_intent_id);
