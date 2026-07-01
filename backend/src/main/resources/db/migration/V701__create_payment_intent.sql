-- WU-PAY-1: Payment intents (LLFR-PAYMENTS-01.1)
-- Payments ADD §7. First migration in the payments band (V7xx).
--
-- A payment_intent records one attempt to charge a fan against a rail. Ownership is granted only
-- once status reaches 'settled' (INV-1, settlement lands in WU-PAY-2). Money is stored in integer
-- minor units (pesewas), never decimals (INV-11). The idempotency_key UNIQUE constraint is the
-- durable backstop that guarantees at most one intent (and one provider charge) per key (PRD §9.2).

CREATE TABLE payment_intent (
    id                  TEXT PRIMARY KEY,
    order_ref           TEXT NOT NULL,
    amount_minor        BIGINT NOT NULL CHECK (amount_minor >= 0),
    currency            TEXT NOT NULL DEFAULT 'GHS',
    provider            TEXT NOT NULL CHECK (provider IN ('mtn','telecel','airteltigo','card','bank')),
    method_kind         TEXT NOT NULL CHECK (method_kind IN ('momo','bank','card')),
    provider_ref        TEXT,
    status              TEXT NOT NULL DEFAULT 'pending'
                            CHECK (status IN ('pending','settled','failed','timeout')),
    failure_reason      TEXT,
    idempotency_key     TEXT NOT NULL,
    request_fingerprint TEXT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_intent_idem UNIQUE (idempotency_key)
);

-- Timeout/reconciliation poll (WU-PAY-2) scans pending intents by age.
CREATE INDEX idx_payment_intent_status_created ON payment_intent (status, created_at);

-- Reconciliation and order lookups join by order reference.
CREATE INDEX idx_payment_intent_order_ref ON payment_intent (order_ref);
