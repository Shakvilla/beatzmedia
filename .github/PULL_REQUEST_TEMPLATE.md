## Work unit
- **WU:** WU-XXX-N  <!-- from BACKEND-PRD.md §8 -->
- **HLFR / LLFRs:** HLFR-XXX-NN · LLFR-XXX-NN.M, LLFR-XXX-NN.M
- **Module(s):** <identity | catalog | payments | commerce | platform | …>
- **Depends on (merged):** WU-XXX-N  <!-- per §8.1 dependency graph; none ⇒ "none" -->

## Summary
<!-- 2–4 sentences: what changed and why. Reference the invariant(s) enforced, e.g. INV-1. -->

## Definition of Done (01-conventions §11 — CI enforces; tick what holds)
- [ ] Unit tests pass (domain + use cases with fakes)
- [ ] Integration tests pass (Testcontainers Postgres/MinIO, REST-assured)
- [ ] Contract conformance green (responses validate against API-CONTRACT.md / frontend types)
- [ ] Flyway migration(s) forward-only, apply cleanly on an empty DB
- [ ] Boots under `docker compose up` (healthy)
- [ ] Hexagonal dependency rule holds (ArchUnit green)
- [ ] Money/side-effect paths idempotent; privileged mutations append an AuditEntry (INV-10)
- [ ] Coverage ≥ gate (testing-strategy.md); Spotless clean; no new high/critical security findings
- [ ] Relevant module **ADD updated in this PR** (if behavior changed)

## Test evidence
<!-- Paste/summarize: test counts, coverage %, key scenarios. CI artifacts are linked automatically. -->
- Unit: <N passed> · Integration: <N passed> · Contract: <pass/fail>
- Coverage: <line %> / <branch %>  (gate: <X%>)

## Migrations
<!-- List each new migration file, or "none". Never edit a merged migration. -->
- `V<n>__<snake_desc>.sql` — <one-line purpose>

## Breaking changes
- [ ] None
<!-- If any: describe contract/migration impact. Use a `BREAKING CHANGE:` footer in the squash commit. -->

## Labels applied
<!-- area:<module>, plus area:payments / area:security if touched, autonomous or needs-human -->

<!-- Closes #<issue-number> -->
