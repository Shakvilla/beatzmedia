---
description: Continuous autonomous build — loop through every READY work unit across all phases until done, a human gate, or a repeated failure.
allowed-tools: Bash, Read, Edit, Write, Glob, Grep, Task
---

Run the BeatzClik backend build autonomously to completion. Orchestrate via the **tech-lead** agent.
Ground in `backend/docs/sdlc/agent-workflow.md`, root `CLAUDE.md`, and `backend/.project/backlog.yaml`.

**The loop.** Repeatedly execute the `/build-next-wu` flow across all phases in order: select the next
READY WU (status `todo`, all `depends_on` `done`, lowest phase, critical path first), implement via the
owner agent + **implement-work-unit**, verify via **run-verification-gate**, open via
**open-pull-request**, review (code-reviewer + security-reviewer), and merge on green. After each WU the
**scrum-master** updates `backlog.yaml` and reports a one-line progress note. Then re-enter the loop —
a merge unblocks downstream WUs.

**NEVER mark a WU `done` until its PR is merged and CI is green.** No self-certification of money
correctness; money/ownership WUs get a verification sub-agent (INV-1/6/9, split math).

**Stop conditions (be explicit about which fired):**
- (a) **All done** — every WU in `backlog.yaml` has status `done`. Report final completion.
- (b) **Human gate hit** — WU-PAY-3 reaches OQ-2 (tip fee %) or OQ-4 (royalty model), or a deploy needs
  prod secrets that are absent. Apply the documented default for OQ-2/OQ-4 to keep building, but PAUSE
  and flag for human confirmation before any prod money/deploy; for missing secrets, pause and state
  exactly which GitHub Environment secrets are needed.
- (c) **Repeated failure** — a WU fails its verification gate twice. Stop on that WU, capture the
  failure, and request human help rather than looping indefinitely.

On any stop, report which condition fired, the current backlog state, open PRs, and the recommended
next action.
