---
description: Report build progress — per-phase WU status, the next READY work units, pending human gates, and open PRs.
allowed-tools: Bash, Read, Glob, Grep, Task
---

Report the current state of the BeatzClik backend build. Ground in `backend/.project/backlog.yaml` and
`backend/docs/sdlc/agent-workflow.md`.

1. **Per-phase status.** Run `bash backend/scripts/progress.sh` to print, per phase, each WU's status
   (`done` / `in_progress` / `in_review` / `blocked` / `todo`).
2. **Next up.** Identify the next READY WU(s): `status: todo` with every `depends_on` `done`, lowest
   phase first, critical path highlighted.
3. **Human gates.** List any pending gates — OQ-2 / OQ-4 on WU-PAY-3 (defaults applied, awaiting
   confirmation) and any deploy blocked on missing prod secrets.
4. **Open PRs.** Run `gh pr list` and show open PRs with their CI/check state and the WU each maps to.
5. **Narrate.** Have the **scrum-master** turn the above into a concise summary: overall % complete,
   what is in flight, what is blocked and why, what a human must decide, and the recommended next
   command (`/build-next-wu`, `/build-phase <n>`, `/run-loop`, `/groom`, or `/ship`).
