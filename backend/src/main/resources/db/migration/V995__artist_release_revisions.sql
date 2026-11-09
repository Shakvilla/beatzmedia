CREATE TABLE release_revision (
  id              VARCHAR(40) PRIMARY KEY,
  release_id      VARCHAR(40) NOT NULL REFERENCES release(id) ON DELETE CASCADE,
  artist_id       VARCHAR(40) NOT NULL,
  status          VARCHAR(16) NOT NULL CHECK (status IN ('draft', 'in_review', 'rejected', 'approved')),
  version         BIGINT NOT NULL DEFAULT 1,
  payload         TEXT NOT NULL,
  title           VARCHAR(255) NOT NULL,
  genre           VARCHAR(80),
  description     TEXT,
  visibility      VARCHAR(16) NOT NULL,
  scheduled_at    TIMESTAMPTZ,
  cover_image     VARCHAR(500),
  downloadable    BOOLEAN,
  rejection_reason TEXT,
  created_at      TIMESTAMPTZ NOT NULL,
  updated_at      TIMESTAMPTZ NOT NULL,
  submitted_at    TIMESTAMPTZ,
  reviewed_at     TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_release_revision_pending
  ON release_revision (release_id)
  WHERE status IN ('draft', 'in_review');

CREATE INDEX idx_release_revision_release ON release_revision (release_id, created_at DESC);
