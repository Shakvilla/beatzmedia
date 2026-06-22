---
description: Product-owner grooming pass — review backlog priorities, resolve open questions, verify LLFR acceptance criteria, and re-order/annotate the backlog.
allowed-tools: Bash, Read, Edit, Write, Glob, Grep, Task
---

Run a backlog grooming pass on the BeatzClik backend. Drive this via the **product-owner** agent.
Ground in the root `BACKEND-PRD.md` (§8 work units, §11 roadmap, §12 open questions),
`backend/.project/backlog.yaml`, `API-CONTRACT.md`, and `backend/docs/sdlc/agent-workflow.md`.

1. **Review priorities.** Walk `backlog.yaml` against the PRD roadmap and dependency graph. Confirm each
   WU's `phase`, `depends_on`, `owner`, and `llfrs` are correct and that ordering respects the DAG.
2. **Resolve open questions.** For each `OQ-*`, apply the documented default and note it. For the
   economics-changing gates **OQ-2** (tip fee %) and **OQ-4** (royalty model), do NOT decide
   unilaterally: present the recommended default plus the precise decision the human must make, and
   flag both on WU-PAY-3's `human_gate`.
3. **Verify acceptance criteria.** Refine and verify the LLFR acceptance criteria for each WU against
   the PRD and `API-CONTRACT.md` (field names, status codes, error codes). Flag any drift between the
   ADD and the contract — the contract + frontend types win for the API surface.
4. **Re-order + annotate.** Re-prioritize and annotate `backlog.yaml` (critical-path first) where the
   review justifies it; never renumber stable WU ids. Persist edits to `backlog.yaml`.
5. **Summary.** Output a grooming summary: changes made, OQ resolutions (defaults vs. human-needed),
   acceptance-criteria gaps found, and the recommended next READY WU(s).
