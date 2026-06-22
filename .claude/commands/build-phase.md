---
description: Build every work unit in a given phase, in dependency order, until none remain READY in that phase.
argument-hint: "[phase-number]"
allowed-tools: Bash, Read, Edit, Write, Glob, Grep, Task
---

Build the entire phase **$1** of the BeatzClik backend. Orchestrate via the **tech-lead** agent.
Ground in `backend/docs/sdlc/agent-workflow.md`, root `CLAUDE.md`, and `backend/.project/backlog.yaml`.

1. **Scope.** From `backlog.yaml`, take every WU with `phase: $1`. Confirm the prior phase's exit
   criteria hold (per agent-workflow §9) before starting; if not, report and stop.
2. **Loop.** Repeatedly run the `/build-next-wu` flow SCOPED to phase $1: select the next READY WU whose
   `phase == $1` (status `todo`, all `depends_on` are `done`, critical path first), then run the full
   loop — owner agent + **implement-work-unit** + **run-verification-gate** + **open-pull-request** →
   review → merge on green → scrum-master marks `done`. Repeat until no phase-$1 WU is READY.
3. **Parallelize safely.** Where two phase-$1 WUs are independent (disjoint modules, no shared
   `depends_on` in flight, different migration bands), the tech-lead may dispatch them concurrently on
   separate branches. Never start a WU whose dependency is merely in flight (must be merged).
4. **Stop conditions.** Halt if a WU hits a human gate (OQ-2/OQ-4 for WU-PAY-3, or missing deploy
   secrets) or fails its verification gate twice — surface it for human help rather than forcing it.
5. **Summarize.** Report phase $1: WUs completed (with PR links), any still blocked and why, any human
   gates flagged, and whether the phase exit criteria are now met.
