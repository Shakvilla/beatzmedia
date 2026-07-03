-- V944__commerce_order_and_ownership.sql
-- Commerce module (WU-COM-2): checkout orders + ownership grants. Commerce ADD §7.
-- Money in integer minor units (pesewas), INV-11. Ids are TEXT (UUIDv7 strings) matching the
-- codebase-wide convention (see V943 note). "order" is a reserved word so it is always quoted.

-- ---------------------------------------------------------------------------------------------
-- order_ref collision-safety (WU-PLT-1 / WU-COM-1 carryover, PR #10).
-- ---------------------------------------------------------------------------------------------
-- The original Uuidv7IdGenerator.newOrderRef() used an in-memory AtomicLong that reset to 0 on
-- JVM restart and wrapped at % 100000 — unsafe under horizontal scaling / rapid restarts. This
-- WU creates the order table (which holds order_ref = the human reference BZ-YYYY-NNNNN), so the
-- fix lands here: a DB-backed SEQUENCE is the collision-safe monotonic source (survives restarts,
-- coordinates across instances), AND a UNIQUE constraint on the reference column makes any residual
-- collision fail LOUDLY (a constraint violation) instead of silently duplicating an order ref.
CREATE SEQUENCE order_ref_seq AS BIGINT START WITH 1 INCREMENT BY 1 NO CYCLE;

CREATE TABLE "order" (
    id                TEXT         PRIMARY KEY,
    account_id        VARCHAR(64)  NOT NULL,
    reference         VARCHAR(24)  NOT NULL,           -- BZ-YYYY-NNNNN (human order ref)
    status            VARCHAR(16)  NOT NULL DEFAULT 'pending',
    subtotal_minor    BIGINT       NOT NULL,
    fee_minor         BIGINT       NOT NULL,
    total_minor       BIGINT       NOT NULL,
    currency          VARCHAR(8)   NOT NULL DEFAULT 'GHS',
    payment_intent_id VARCHAR(64),
    failure_reason    VARCHAR(256),
    idempotency_key   VARCHAR(128) NOT NULL,           -- checkout Idempotency-Key (INV-1 / §9.2)
    request_hash      VARCHAR(64)  NOT NULL,           -- SHA-256 of the idempotency-relevant request;
                                                       -- same key + different hash => 409 (api-and-contract §5.2)
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_order_status CHECK (status IN ('pending', 'paid', 'fulfilled', 'refunded', 'failed')),
    CONSTRAINT ck_order_subtotal_non_negative CHECK (subtotal_minor >= 0),
    CONSTRAINT ck_order_fee_non_negative CHECK (fee_minor >= 0),
    CONSTRAINT ck_order_total_non_negative CHECK (total_minor >= 0),
    -- Loud collision guard for the human order reference (carryover DoD).
    CONSTRAINT uq_order_reference UNIQUE (reference),
    -- One order per (account, idempotency key): a replayed checkout short-circuits to the same
    -- order (INV-1 / §9.2). Scoped by account so keys never collide across fans.
    CONSTRAINT uq_order_account_idem UNIQUE (account_id, idempotency_key)
);
CREATE INDEX ix_order_account_created ON "order" (account_id, created_at DESC);
CREATE INDEX ix_order_payment_intent ON "order" (payment_intent_id);

CREATE TABLE order_line (
    id               TEXT         PRIMARY KEY,
    order_id         TEXT         NOT NULL REFERENCES "order"(id) ON DELETE CASCADE,
    kind             VARCHAR(16)  NOT NULL,
    ref_id           VARCHAR(64)  NOT NULL,
    title            VARCHAR(256) NOT NULL,
    unit_price_minor BIGINT       NOT NULL,
    currency         VARCHAR(8)   NOT NULL DEFAULT 'GHS',
    qty              INT          NOT NULL,
    CONSTRAINT ck_order_line_kind CHECK (kind IN
        ('track', 'album', 'album-rest', 'store', 'episode', 'season-pass', 'ticket')),
    CONSTRAINT ck_order_line_qty_range CHECK (qty BETWEEN 1 AND 99),
    CONSTRAINT ck_order_line_price_non_negative CHECK (unit_price_minor >= 0)
);
CREATE INDEX ix_order_line_order ON order_line (order_id);

-- ---------------------------------------------------------------------------------------------
-- ownership_grant — created ONLY on confirmed settlement (INV-1), revoked on refund (INV-9).
-- Exactly one of track_id / episode_id set. Active while revoked_at IS NULL.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE ownership_grant (
    id              TEXT         PRIMARY KEY,
    account_id      VARCHAR(64)  NOT NULL,
    track_id        VARCHAR(64),
    episode_id      VARCHAR(64),
    source_order_id TEXT         NOT NULL REFERENCES "order"(id),
    granted_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    revoked_at      TIMESTAMPTZ,
    -- exactly one target (track XOR episode)
    CONSTRAINT ck_grant_single_target CHECK ((track_id IS NOT NULL) <> (episode_id IS NOT NULL))
);

-- One ACTIVE grant per (account, track) / (account, episode); revoked rows are excluded so a
-- re-purchase after a refund is permitted (INV-1/INV-9). This unique-active index is ALSO the
-- durable backstop that makes a re-delivered PaymentSettled idempotent — a duplicate grant insert
-- for the same (account, track) fails on the constraint instead of double-granting.
CREATE UNIQUE INDEX ux_grant_account_track
    ON ownership_grant (account_id, track_id)
    WHERE revoked_at IS NULL AND track_id IS NOT NULL;
CREATE UNIQUE INDEX ux_grant_account_episode
    ON ownership_grant (account_id, episode_id)
    WHERE revoked_at IS NULL AND episode_id IS NOT NULL;
CREATE INDEX ix_grant_source_order ON ownership_grant (source_order_id);
CREATE INDEX ix_grant_account ON ownership_grant (account_id);

-- ---------------------------------------------------------------------------------------------
-- order_grant_posting — the exactly-once CLAIM header for the settlement->grant expansion,
-- keyed by the source order id. Mirrors the payments ledger_posting pattern (WU-PAY-3, ADR-22).
-- The PaymentSettled handler inserts this row BEFORE creating any grant; a concurrent second
-- settlement for the SAME order fails the PRIMARY KEY, so the grant fan-out (which spans many
-- rows across many tracks/episodes) runs exactly once per order — the unique-active grant indexes
-- above are the per-target backstop, this claim is the per-order all-or-nothing gate (INV-1).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE order_grant_posting (
    order_id   TEXT        PRIMARY KEY REFERENCES "order"(id),
    posted_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
