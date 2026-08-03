-- V972__create_taxonomy_term.sql
-- Admin-managed taxonomy: genres and the podcast/event/browse category lists.
--
-- WHY THIS EXISTS. The platform had four taxonomies and not one of them was manageable:
--
--   genre             a TypeScript union in Frontend/src/types/index.ts (9 values)
--   podcast.category  a CHECK constraint in V945 (8 values)
--   event.category    a CHECK constraint in V952 (5 values)
--   browse_category   a real table (V304) — the only one that was already dynamic
--
-- Adding "Afro-fusion" meant editing TypeScript and shipping a build; adding a podcast category
-- meant a schema migration. This table makes all four editable from the admin console.
--
-- The CHECK constraints are dropped below. They have to be: with them in place an admin can create
-- a category and Postgres will reject every row that uses it. This also unblocks the studio podcast
-- projection, which was stuck precisely because studio never validated against that welded list.
--
-- NOTE ON ORDERING: this is V972, not a V1xx platform number, and that is deliberate. Flyway applies
-- strictly by ascending version, so it must run AFTER V945 (podcast) and V952 (event) or the
-- constraints it drops would not exist yet.

CREATE TABLE taxonomy_term (
    id          TEXT        PRIMARY KEY,
    -- Which list this term belongs to. Kept as TEXT + CHECK rather than a Postgres enum so that
    -- adding a fifth taxonomy is an application change, not an ALTER TYPE.
    kind        TEXT        NOT NULL
                CONSTRAINT chk_taxonomy_kind
                CHECK (kind IN ('genre', 'podcast_category', 'event_category', 'browse_category')),
    -- Stable machine key. Never shown to users, never edited after creation — renaming a term
    -- changes `label`, so existing rows that reference it keep working.
    slug        TEXT        NOT NULL,
    -- What users see. This is what release.genre / podcast.category / event.category actually
    -- store today, so the seeds below MUST match those columns byte for byte.
    label       TEXT        NOT NULL,
    -- Tailwind gradient classes for the home "Browse by mood & genre" tiles. Only meaningful for
    -- kind='browse_category'; NULL everywhere else.
    color_class TEXT,
    sort_order  INTEGER     NOT NULL DEFAULT 0,
    -- Soft availability. An inactive term disappears from every picker but stays valid on rows
    -- that already reference it, so deactivating never rewrites published content.
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT taxonomy_term_kind_slug_uk UNIQUE (kind, slug),
    -- Labels are what the consuming columns store, so they must be unique per kind too — two
    -- genres both labelled "Drill" would make release.genre ambiguous.
    CONSTRAINT taxonomy_term_kind_label_uk UNIQUE (kind, label)
);

CREATE INDEX taxonomy_term_kind_idx ON taxonomy_term (kind, sort_order);

-- ---------------------------------------------------------------------------
-- Seed from the values that were previously hardcoded.
--
-- These are a MIGRATION of existing constants, not demo data: dropping the CHECK constraints
-- without carrying the values across would orphan every podcast and event already in the database.
-- ---------------------------------------------------------------------------

-- Genres — from the TypeScript union in Frontend/src/types/index.ts.
INSERT INTO taxonomy_term (id, kind, slug, label, sort_order) VALUES
    ('tax-genre-afrobeats', 'genre', 'afrobeats', 'Afrobeats', 1),
    ('tax-genre-hiplife',   'genre', 'hiplife',   'Hiplife',   2),
    ('tax-genre-highlife',  'genre', 'highlife',  'Highlife',  3),
    ('tax-genre-amapiano',  'genre', 'amapiano',  'Amapiano',  4),
    ('tax-genre-drill',     'genre', 'drill',     'Drill',     5),
    ('tax-genre-gospel',    'genre', 'gospel',    'Gospel',    6),
    ('tax-genre-rnb',       'genre', 'rnb',       'R&B',       7),
    ('tax-genre-reggae',    'genre', 'reggae',    'Reggae',    8),
    ('tax-genre-jazz',      'genre', 'jazz',      'Jazz',      9);

-- Podcast categories — exactly the chk_pod_category list from V945.
INSERT INTO taxonomy_term (id, kind, slug, label, sort_order) VALUES
    ('tax-pod-news',         'podcast_category', 'news-politics', 'News & Politics', 1),
    ('tax-pod-comedy',       'podcast_category', 'comedy',        'Comedy',          2),
    ('tax-pod-business',     'podcast_category', 'business',      'Business',        3),
    ('tax-pod-sports',       'podcast_category', 'sports',        'Sports',          4),
    ('tax-pod-culture',      'podcast_category', 'culture',       'Culture',         5),
    ('tax-pod-tech',         'podcast_category', 'tech',          'Tech',            6),
    ('tax-pod-health',       'podcast_category', 'health',        'Health',          7),
    ('tax-pod-storytelling', 'podcast_category', 'storytelling',  'Storytelling',    8);

-- Event categories — exactly the chk_event_category list from V952.
INSERT INTO taxonomy_term (id, kind, slug, label, sort_order) VALUES
    ('tax-evt-concert',   'event_category', 'concert',         'Concert',          1),
    ('tax-evt-festival',  'event_category', 'festival',        'Festival',         2),
    ('tax-evt-club',      'event_category', 'club-night',      'Club Night',       3),
    ('tax-evt-listening', 'event_category', 'listening-party', 'Listening Party',  4),
    ('tax-evt-tour',      'event_category', 'tour',            'Tour',             5);

-- Browse categories — carried over from the existing browse_category table (V304) so the home
-- page keeps its tiles and their colours. Done as INSERT...SELECT because that table is already
-- populated in every environment; the source table is dropped at the end of this migration.
INSERT INTO taxonomy_term (id, kind, slug, label, color_class, sort_order)
SELECT
    'tax-browse-' || b.id,
    'browse_category',
    b.id,
    b.title,
    b.color_class,
    row_number() OVER (ORDER BY b.title)
FROM browse_category b;

-- ---------------------------------------------------------------------------
-- Release the welded constraints.
--
-- Enforcement moves to the application layer, which validates a submitted label against the ACTIVE
-- terms of the right kind. A foreign key would be stronger, but these columns store the label
-- ('News & Politics'), not an id, so an FK would require rewriting every existing row plus a
-- composite key on (kind, label) — a much larger and riskier change for the same guarantee.
-- ---------------------------------------------------------------------------

ALTER TABLE podcast DROP CONSTRAINT chk_pod_category;
ALTER TABLE event   DROP CONSTRAINT chk_event_category;

-- browse_category is now a duplicate source of truth for the same data; the rows are safely copied
-- above, so drop it rather than leave two tables that can disagree.
DROP TABLE browse_category;
