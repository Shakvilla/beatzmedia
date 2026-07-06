-- V949__analytics_rollups.sql
-- Analytics module (WU-ANA-1): sales_rollup / audience_rollup + the staged-fact tables the rollup
-- jobs fold from. Analytics ADD §7.
--
-- Design note: analytics never reads another module's tables (hexagonal dependency rule). The four
-- *_fact tables below are populated ONLY by analytics' own CDI event observers reacting to canonical
-- domain events (commerce.SaleRecorded, payments.TipReceived, playback.PlayRecorded,
-- library.Followed) — they are analytics-owned staging tables, not projections of another module's
-- schema. ids referenced (artist_id, account_id) are bare TEXT — no cross-module FK (conventions §6).

-- sales_rollup -------------------------------------------------------------
CREATE TABLE sales_rollup (
    id            TEXT        PRIMARY KEY,
    artist_id     TEXT        NOT NULL,
    bucket        DATE        NOT NULL,
    grain         TEXT        NOT NULL CHECK (grain IN ('DAILY','WEEKLY','MONTHLY')),
    sales_minor   BIGINT      NOT NULL DEFAULT 0,
    tips_minor    BIGINT      NOT NULL DEFAULT 0,
    royalty_minor BIGINT      NOT NULL DEFAULT 0 CHECK (royalty_minor = 0), -- OQ-4: no royalty model
    units         INTEGER     NOT NULL DEFAULT 0,
    computed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_sales_rollup UNIQUE (artist_id, bucket, grain)
);
CREATE INDEX ix_sales_rollup_lookup ON sales_rollup (artist_id, grain, bucket);

-- audience_rollup ------------------------------------------------------------
CREATE TABLE audience_rollup (
    id               TEXT        PRIMARY KEY,
    artist_id        TEXT        NOT NULL,
    bucket           DATE        NOT NULL,
    grain            TEXT        NOT NULL CHECK (grain IN ('DAILY','WEEKLY','MONTHLY')),
    plays            BIGINT      NOT NULL DEFAULT 0,
    followers_gained INTEGER     NOT NULL DEFAULT 0,
    unique_listeners INTEGER     NOT NULL DEFAULT 0,
    completion_pct   INTEGER     NOT NULL DEFAULT 0 CHECK (completion_pct BETWEEN 0 AND 100),
    computed_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_audience_rollup UNIQUE (artist_id, bucket, grain)
);
CREATE INDEX ix_audience_rollup_lookup ON audience_rollup (artist_id, grain, bucket);

-- analytics_sale_fact --------------------------------------------------------
-- Staged from commerce's SaleRecorded event (one row per creator per settled order line group).
CREATE TABLE analytics_sale_fact (
    id           TEXT        PRIMARY KEY,
    artist_id    TEXT        NOT NULL,
    gross_minor  BIGINT      NOT NULL,
    currency     TEXT        NOT NULL,
    occurred_at  TIMESTAMPTZ NOT NULL,
    processed    BOOLEAN     NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_analytics_sale_fact_unprocessed ON analytics_sale_fact (occurred_at)
    WHERE NOT processed;

-- analytics_tip_fact ---------------------------------------------------------
-- Staged from payments' TipReceived event.
CREATE TABLE analytics_tip_fact (
    id                  TEXT        PRIMARY KEY,
    artist_id           TEXT        NOT NULL,
    creator_share_minor BIGINT      NOT NULL,
    currency            TEXT        NOT NULL,
    occurred_at         TIMESTAMPTZ NOT NULL,
    processed           BOOLEAN     NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_analytics_tip_fact_unprocessed ON analytics_tip_fact (occurred_at)
    WHERE NOT processed;

-- analytics_play_fact ---------------------------------------------------------
-- Staged from playback's PlayRecorded event; artist_id resolved via catalog's GetTrack input port
-- at observation time (never a catalog table read). account_id is nullable (anonymous plays).
CREATE TABLE analytics_play_fact (
    id          TEXT        PRIMARY KEY,
    artist_id   TEXT        NOT NULL,
    account_id  TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    processed   BOOLEAN     NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_analytics_play_fact_unprocessed ON analytics_play_fact (occurred_at)
    WHERE NOT processed;

-- analytics_follow_fact -------------------------------------------------------
-- Staged from library's Followed event, kind=artist only.
CREATE TABLE analytics_follow_fact (
    id          TEXT        PRIMARY KEY,
    artist_id   TEXT        NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    processed   BOOLEAN     NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_analytics_follow_fact_unprocessed ON analytics_follow_fact (occurred_at)
    WHERE NOT processed;
