-- V803__search_document_price_minor.sql
-- Search index lifecycle (WU-SRCH-1 security fix F2): add typed price_minor column for safe price
-- sort. Replacing the previous CAST(payload->>'price_minor' AS BIGINT) in ORDER BY, which would
-- throw a Postgres cast error on any non-numeric JSONB value causing a 500 on every price-sorted
-- query. The column is nullable (BIGINT NULL) — non-priced entities store NULL, ORDER BY NULLS LAST
-- keeps them at the end of price sort results. Populated on upsert by the application adapter.
-- Band: V8xx (search/store). Never edit V801/V802.

ALTER TABLE search_document
    ADD COLUMN IF NOT EXISTS price_minor BIGINT NULL;

-- Index to support efficient ORDER BY price_minor ASC/DESC NULLS LAST (store price sort).
CREATE INDEX IF NOT EXISTS idx_search_document_price_minor
    ON search_document (price_minor ASC NULLS LAST);
