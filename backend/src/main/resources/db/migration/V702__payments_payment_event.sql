-- WU-PAY-2: Provider webhooks + timeout poll + reconciliation (LLFR-PAYMENTS-01.2 .. 01.4).
-- Payments ADD §7. Second migration in the payments band (V7xx). Forward-only.
--
-- Ids are UUIDv7 strings (TEXT, matching payment_intent.id from V701), not the UUID type; enum-like
-- columns use TEXT + CHECK for additive evolution.

-- ---------------------------------------------------------------------------
-- payment_event — the idempotency backstop for HandleProviderWebhook (01.2).
-- provider_event_id is UNIQUE so a duplicate/replayed webhook applies the settlement transition at
-- most once (INSERT ... ON CONFLICT DO NOTHING in the adapter). payload stores the verbatim raw body
-- as JSONB so a later reconciliation can re-derive from provider truth without re-parsing at ingest.
-- ---------------------------------------------------------------------------
CREATE TABLE payment_event (
    id                TEXT PRIMARY KEY,
    intent_id         TEXT NOT NULL REFERENCES payment_intent(id),
    provider_event_id TEXT NOT NULL,
    type              TEXT NOT NULL CHECK (type IN ('SETTLED','FAILED','PENDING')),
    payload           JSONB NOT NULL,
    received_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_event_provider UNIQUE (provider_event_id)
);

-- Webhook handling resolves/records events per intent; index the FK for those lookups.
CREATE INDEX idx_payment_event_intent ON payment_event (intent_id);

-- ---------------------------------------------------------------------------
-- reconciliation_discrepancy — finance risk signals from the daily reconciliation (01.4).
-- The double-entry ledger lands in WU-PAY-3, so this WU reconciles provider-reported settlement
-- against the payment_intent's own status; kinds are constrained to the two mismatches detectable
-- today. UNIQUE (intent_id, kind, as_of_day) makes the daily job idempotent — re-running over the
-- same window records each distinct mismatch at most once.
-- ---------------------------------------------------------------------------
CREATE TABLE reconciliation_discrepancy (
    id              TEXT PRIMARY KEY,
    intent_id       TEXT NOT NULL REFERENCES payment_intent(id),
    order_ref       TEXT NOT NULL,
    kind            TEXT NOT NULL
                        CHECK (kind IN ('PROVIDER_SETTLED_INTENT_NOT','PROVIDER_FAILED_INTENT_SETTLED')),
    amount_minor    BIGINT NOT NULL CHECK (amount_minor >= 0),
    provider_status TEXT NOT NULL,
    intent_status   TEXT NOT NULL,
    as_of_day       TEXT NOT NULL,   -- ISO-8601 date (yyyy-MM-dd) of the reconciliation window
    detected_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_recon_discrepancy UNIQUE (intent_id, kind, as_of_day)
);

-- Finance review lists discrepancies by day.
CREATE INDEX idx_recon_discrepancy_day ON reconciliation_discrepancy (as_of_day);
