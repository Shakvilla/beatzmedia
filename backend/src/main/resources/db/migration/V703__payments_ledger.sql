-- WU-PAY-3: Double-entry ledger + creator balance + split posting + tips
-- (LLFR-PAYMENTS-02.1 .. 02.3, LLFR-PAYMENTS-05). Payments ADD §7. Third migration in the payments
-- band (V7xx). Forward-only; never edits V701/V702.
--
-- Every monetary movement is double-entry and balanced (INV-6): for every txn_id, Σ DEBIT = Σ CREDIT.
-- The application layer asserts this in-app before flush; the deferred constraint trigger below is the
-- DB-level backstop that makes an unbalanced posting impossible to commit.
--
-- Money is integer minor units (pesewas), INV-11. Ledger account ids are UUIDv7 strings (TEXT,
-- matching the rest of the codebase — see payment_intent.id in V701); enum-like columns use TEXT +
-- CHECK for additive evolution (consistent with V701/V702), NOT the PG enum type that the ADD's
-- illustrative DDL sketched.

-- ---------------------------------------------------------------------------
-- ledger_account — accounts partitioned by kind (payments ADD §3 "Double-entry ledger design").
--   provider_clearing : funds in transit from a rail, cleared on settlement (normal debit)
--   creator_payable   : what the platform owes a creator, owner_account_id = creator (normal credit)
--   platform_revenue  : the platform's fee take, singleton (normal credit)
--   payout_clearing   : funds reserved/in-flight for withdrawals (normal debit) — used from WU-PAY-4
-- ---------------------------------------------------------------------------
CREATE TABLE ledger_account (
    id               TEXT PRIMARY KEY,
    kind             TEXT NOT NULL
                         CHECK (kind IN ('provider_clearing','creator_payable','platform_revenue','payout_clearing')),
    owner_account_id TEXT,   -- creator account for creator_payable; NULL for the shared/singleton kinds
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- A creator has exactly one creator_payable account; a provider has exactly one provider_clearing
-- account; platform_revenue / payout_clearing are singletons (owner_account_id NULL). Partial unique
-- indexes make get-or-create idempotent under concurrency (ON CONFLICT target).
CREATE UNIQUE INDEX uq_ledger_account_creator_payable
    ON ledger_account (owner_account_id)
    WHERE kind = 'creator_payable';
CREATE UNIQUE INDEX uq_ledger_account_provider_clearing
    ON ledger_account (owner_account_id)
    WHERE kind = 'provider_clearing';
CREATE UNIQUE INDEX uq_ledger_account_singleton
    ON ledger_account (kind)
    WHERE kind IN ('platform_revenue','payout_clearing');
CREATE INDEX ix_ledger_account_owner ON ledger_account (owner_account_id);

-- ---------------------------------------------------------------------------
-- ledger_entry — one balanced row per posting; a txn_id groups the rows of a single transaction.
-- amount_minor is always POSITIVE; direction carries the sign (DEBIT/CREDIT). ref_type/ref_id trace
-- the entry back to its source (e.g. 'intent'/'tip' + the intent id).
-- ---------------------------------------------------------------------------
CREATE TABLE ledger_entry (
    id           TEXT PRIMARY KEY,
    txn_id       TEXT NOT NULL,
    account_id   TEXT NOT NULL REFERENCES ledger_account(id),
    direction    TEXT NOT NULL CHECK (direction IN ('DEBIT','CREDIT')),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    ref_type     TEXT NOT NULL,
    ref_id       TEXT NOT NULL,
    cleared_at   TIMESTAMPTZ,
    posted_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_ledger_txn ON ledger_entry (txn_id);
CREATE INDEX ix_ledger_account_cleared ON ledger_entry (account_id, cleared_at);
CREATE INDEX ix_ledger_entry_ref ON ledger_entry (ref_type, ref_id);

-- ---------------------------------------------------------------------------
-- Per-txn balance backstop (INV-6): the signed sum per txn_id must be zero (Σdebits = Σcredits).
-- Enforced at COMMIT via a DEFERRABLE INITIALLY DEFERRED constraint trigger so a multi-row posting is
-- checked once, after all rows of the txn_id are inserted — a transient imbalance mid-insert is fine,
-- an imbalance at commit is rejected. This is the durable backstop under the application's own
-- in-app assertion in LedgerRepository.postBalanced.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION assert_txn_balanced() RETURNS trigger AS $$
BEGIN
  IF (SELECT COALESCE(SUM(CASE WHEN direction = 'DEBIT' THEN amount_minor ELSE -amount_minor END), 0)
        FROM ledger_entry WHERE txn_id = NEW.txn_id) <> 0 THEN
    RAISE EXCEPTION 'ledger txn % not balanced (Sum debits != Sum credits, INV-6)', NEW.txn_id;
  END IF;
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_ledger_balanced
  AFTER INSERT ON ledger_entry
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE FUNCTION assert_txn_balanced();

-- ---------------------------------------------------------------------------
-- creator_balance — read-model projection of a creator's derived balance (payments ADD §3):
--   available_minor = Σ cleared creator_payable CREDIT − Σ cleared cash-out DEBIT
--   pending_minor   = Σ uncleared creator_payable CREDIT
--   lifetime_minor  = Σ all creator_payable CREDIT ever (gross lifetime earnings)
-- Refreshed inside the same transaction as each posting/clear so it never drifts from the entries.
-- ---------------------------------------------------------------------------
CREATE TABLE creator_balance (
    account_id      TEXT PRIMARY KEY,
    available_minor BIGINT NOT NULL DEFAULT 0,
    pending_minor   BIGINT NOT NULL DEFAULT 0,
    lifetime_minor  BIGINT NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
