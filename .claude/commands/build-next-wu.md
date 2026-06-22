---
description: Build the single next READY work unit end-to-end — implement, verify, open PR, review, merge on green, mark done.
argument-hint: "[WU-ID]"
allowed-tools: Bash, Read, Edit, Write, Glob, Grep, Task
---

Build ONE work unit to completion. Orchestrate via the **tech-lead** agent. Ground in
`backend/docs/sdlc/agent-workflow.md`, root `CLAUDE.md`, and `backend/.project/backlog.yaml`.

1. **Select the WU.** If `$1` is given, force that WU id (verify it is actually READY — every
   `depends_on` is `done` — and refuse with an explanation if it is blocked). Otherwise have the
   tech-lead pick the next READY WU from `backlog.yaml`: `status: todo`, all `depends_on` are `done`,
   lowest phase first, and among ties the one unblocking the most downstream WUs (critical path).
   If nothing is READY, report that (and any blocking gate) and stop.
2. **Claim it.** scrum-master sets the WU `in_progress` (via `backend/scripts/progress.sh` or by editing
   `backlog.yaml`) with the branch `feat/<wu-id>-<slug>`. One WU per branch.
3. **Implement.** Delegate to the WU's `owner` agent (see backlog) and run the **implement-work-unit**
   skill: read spec → plan → branch → implement strictly inside the hexagonal architecture →
   additive Flyway migration in the module's version band → unit + integration + contract tests →
   update the module ADD in the same change.
4. **Verify.** Run the **run-verification-gate** skill (the full local gate: build, ArchUnit, contract,
   migration IT, Compose smoke). For money/ownership WUs (WU-PAY-*, WU-COM-2, ledger/KYC) spawn a
   verification sub-agent to adversarially re-derive split math and INV-1/6/9 idempotency. Loop back to
   step 3 on any red.
5. **Open PR.** Run the **open-pull-request** skill — PR with the DoD checklist, LLFR traceability, and
   any applied OQ defaults.
6. **Review + merge.** **code-reviewer** and **security-reviewer** review; address change requests by
   looping to step 3. Auto-merge (squash) ONLY when all required CI checks are green.
7. **Close out.** scrum-master sets the WU `done` — NEVER before the PR is merged and CI is green.
   Report: WU id, branch, PR link, gate result, and which downstream WUs are now READY.
