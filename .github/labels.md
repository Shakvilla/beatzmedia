# Label scheme — BeatzClik backend

A small, mechanical label scheme decides whether a PR can merge autonomously or
needs a human, and provides routing/tracking facets. See
`backend/docs/sdlc/branching-and-pr.md` §5 / §6.2. Path-based labels are applied
automatically by `actions/labeler` (`.github/labeler.yml`).

## Merge-gating labels

| Label          | Color (suggested) | Meaning / effect                                                                 |
|----------------|-------------------|----------------------------------------------------------------------------------|
| `area:payments`| `#b60205` (red)   | Touches `payments` / `commerce` / `ledger` money paths. **Forces human review.** |
| `area:security`| `#b60205` (red)   | Touches auth, RBAC, JWT, secrets, crypto, or `security-scan` config. **Forces human review.** |
| `needs-human`  | `#d93f0b` (orange)| Explicit human gate (rule- or reviewer-applied when confidence is low).          |
| `autonomous`   | `#0e8a16` (green) | Eligible for fully automated merge once checks + automated review pass.           |

## Area labels (one per touched module)

| Label             | Module / bounded context                  |
|-------------------|-------------------------------------------|
| `area:identity`   | identity (auth, accounts) — also security |
| `area:catalog`    | catalog (releases, tracks)                |
| `area:commerce`   | commerce (orders, ownership) — payments   |
| `area:platform`   | platform settings, cross-cutting          |
| `area:build`      | Maven / Docker / tooling                   |
| `area:ci`         | CI/CD workflows, dependabot                |

> Add further `area:<module>` labels as modules land (e.g. `area:store`,
> `area:podcast`, `area:events`, `area:notifications`, `area:studio`,
> `area:analytics`, `area:admin`) — one per touched module.

## Phase labels (issues — milestones in §8.1)

| Label     | Phase                                   |
|-----------|-----------------------------------------|
| `phase:0` | Phase 0 — Foundations                   |
| `phase:1` | Phase 1 — Identity & Catalog            |
| `phase:2` | Phase 2 — Commerce & Payments           |
| `phase:3` | Phase 3 — Money completion              |
| `phase:4` | Phase 4 — Surfaces & proposals          |

## Type labels (Conventional Commit type)

| Label        | Conventional type     |
|--------------|-----------------------|
| `type:feat`  | feat (minor bump)     |
| `type:fix`   | fix (patch bump)      |
| `type:chore` | chore                 |
| `type:ci`    | ci                    |
| `type:docs`  | docs                  |
| `type:test`  | test                  |

## How the gate works

1. `actions/labeler` applies `area:*` labels from changed paths.
2. Any `area:payments` or `area:security` label -> a human approval is required
   (CODEOWNERS routes the matching team) before auto-merge can fire.
3. Otherwise the PR is `autonomous`: with all required checks green and the
   reviewer agent's approval, GitHub auto-merge squashes it to `main`.
