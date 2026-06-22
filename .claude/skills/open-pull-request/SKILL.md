---
name: open-pull-request
description: Open a PR for a BeatzClik work unit using open-pr.sh (or gh) — ensure the feat/<WU-ID>-slug branch, a Conventional Commit with the WU id in scope, the PR template (linked WU/LLFRs, DoD checklist, test evidence, migrations, ADD box), the right labels, and squash auto-merge. Use after the verification gate is fully green.
allowed-tools: Bash, Read, Edit
---

# Open a Pull Request

One WU per branch, one PR per branch; the PR title becomes the squash commit on `main`.
**Only open a PR after run-verification-gate is fully green.** Source: `branching-and-pr.md`.

## 1. Branch & commits
- Branch named `feat/<WU-ID>-<kebab-slug>` (type ∈ feat|fix|chore|docs|refactor|test|build|ci), branched from latest `origin/main` (rebase, don't merge `main` in).
- Atomic Conventional Commits, WU id as the first subject token:
  `feat(<module>): <WU-ID> <imperative subject ≤72 chars>` — e.g. `feat(identity): WU-IDN-1 account registration with Argon2id hashing`.
- The **PR title must itself be a valid Conventional Commit** (it becomes the squash commit).

## 2. Open the PR
```bash
backend/scripts/open-pr.sh <WU-ID>
```
(or `gh pr create` directly). It pushes the branch and creates the PR from `.github/PULL_REQUEST_TEMPLATE.md`. Fill the template:
- **Work unit:** WU id, HLFR/LLFRs, module(s), `Depends on (merged)` list.
- **Summary:** 2–4 sentences (name the invariants enforced, e.g. INV-1, INV-11).
- **Definition of Done:** tick every box truthfully (it mirrors 01-conventions §11; a PR cannot auto-merge with unchecked boxes).
- **Test evidence:** unit/integration/contract counts, coverage % vs gate.
- **Migrations:** list each new `V<n>__*.sql` (or "none").
- **Breaking changes:** "None", or a `BREAKING CHANGE:` footer describing contract/migration impact.
- **ADD updated** box ticked if behavior changed. `Closes #<issue>`.
- Note any `OQ-*` default applied.

## 3. Labels
Apply `area:<module>` (one per touched module). Add `area:payments` when touching `payments`/`commerce` money paths and `area:security` when touching auth/RBAC/JWT/secrets/crypto — **both force human review.** Add the `phase:<n>` label. The path-based auto-labeler also applies `autonomous` (or you add `needs-human` when uncertain).

## 4. Enable auto-merge (squash)
```bash
gh pr merge --squash --auto --delete-branch
```
It merges automatically once: all required checks are green on the head commit and the branch is up to date with `main`; the reviewer agent approved (plus a human if `area:payments`/`area:security`/breaking/`needs-human`); no unresolved conversations. Squash keeps one commit per WU on `main`; the branch deletes on merge.

## 5. After opening
Set the WU `status: in_review` in `backlog.yaml` (via `progress.sh`). If a required check flakes, re-run it — never bypass branch protection. Hand off to review-pull-request for the reviewer pass.
