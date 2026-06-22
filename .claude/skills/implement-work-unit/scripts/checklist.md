<!-- Per-WU Definition of Done — paste into the PR body and tick truthfully. -->
## Definition of Done (01-conventions §11 — CI enforces)
- [ ] Unit tests pass (domain + use cases with fakes)
- [ ] Integration tests pass (Testcontainers Postgres/MinIO, REST-assured)
- [ ] Contract conformance green (responses validate against API-CONTRACT.md / frontend types)
- [ ] Flyway migration(s) forward-only, apply cleanly on an empty DB; `flyway.validate()` passes
- [ ] Boots under `docker compose up` (healthy; `/q/health/ready` 200)
- [ ] Hexagonal dependency rule holds (ArchUnit green)
- [ ] Money/side-effect paths idempotent; privileged mutations append an AuditEntry (INV-10)
- [ ] Coverage ≥ gate (testing-strategy.md); Spotless clean; no new high/critical security findings
- [ ] Relevant module ADD updated in this PR (if behavior changed)

## Traceability
- WU: WU-XXX-N · LLFRs: LLFR-XXX-NN.M · Module(s): <module> · Depends on (merged): WU-XXX-N
- OQ / human_gate (default applied): <none | OQ-N: default …>
- Migrations: V<n>__<desc>.sql — <purpose>
