-- Downloadable releases: the permission as it stood when this grant was created.
--
-- Captured at settlement and never updated. An artist switching downloads off affects FUTURE sales
-- only — PRD OQ-8 already preserves owners' downloads through a takedown, a more adversarial event
-- than changing one's mind.
--
-- DEFAULT false only so the column can be NOT NULL on an existing table; every write sets it
-- explicitly. false rather than true because if a future insert path forgets to set it, the grant
-- denies a download rather than granting one the artist never agreed to.
ALTER TABLE ownership_grant ADD COLUMN downloadable boolean NOT NULL DEFAULT false;
