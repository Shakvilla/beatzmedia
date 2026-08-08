# Admin Console — QA Gap Report

**Date:** 2026-08-08
**Scope:** Entire admin surface — 19 UI routes, 61 `/v1/admin/*` endpoints, and the system-wide
controls the admin console claims to own.
**Method:** Static wiring audit + authenticated endpoint sweep + manual UI walkthrough of every page,
against a near-empty database (1 release, 2 accounts, 16 store items).
**Status:** Documentation only. Nothing in this report has been fixed.

---

## How to read this

Findings are ranked by what they'd cost in production, not by effort to fix. Each has the evidence
that produced it, so you can re-run any of them.

A note on the empty database: it made empty-state rendering easy to verify and made data-heavy
behaviour (pagination at volume, queue triage, dispute handling) impossible to verify. Everything I
could not exercise is listed in [§7 Not tested](#7-what-i-could-not-test), not silently omitted.

---

## 1. What is genuinely solid

I tried to break these and could not. Stating them because a gap report that lists only failures
gives a false picture of where the risk actually is.

| Area | Evidence |
|---|---|
| **Authentication** | All 61 admin endpoints return `401` to an anonymous caller. No bypass, no endpoint left open. |
| **Authorization** | Every endpoint is role-guarded, and **every role boundary is now proven by execution**, not by reading annotations — see `AdminRoleMatrixIT`: 61 endpoints × 5 roles, both directions, all green. Money → `{finance, super-admin}`; settings/compliance/audit/team → `super-admin` only. A plain fan token is refused by all 61. |
| **Audit coverage** | Every mutating admin service appends an `AuditEntry`. `Get*`/`List*` services correctly do not. |
| **Idempotency** | `POST /finance/payouts/run-weekly` and `/payouts/{id}/send` reject with `400 MISSING_IDEMPOTENCY_KEY` when the header is absent. |
| **Moderation gate** | A release submitted by an artist stays `in_review` and only reaches `live` on an explicit admin approve, which writes `APPROVE_RELEASE` to the audit log. Verified end to end on release `019fe1af`. |
| **Error envelope** | Consistent `{error:{code,message,field}}` across all 61. Unknown ids give `404`; missing required fields give `422` with the offending `field` named. |
| **Taxonomy usage counts** | Cross-checked against the database: Afrobeats "4 in use" = 3 store items + 1 release. Exact. |

---

## 2. Blockers — do not ship

### GAP-01 · Two admin detail pages are unreachable dead code

`/admin/users/{id}` and `/admin/catalog/{id}` render their **parent list page** instead of the
detail view. The URL changes, the `<h1>` does not.

**Root cause.** `admin.users.tsx` and `admin.catalog.tsx` are leaf components that also act as
parents to `$userId` / `$itemId` children, but neither renders an `<Outlet/>` and neither has an
`.index.tsx` sibling. `admin.finance.tsx` does both — which is exactly why the finance sub-routes
work and these do not.

**Impact.** Two fully-built pages — roughly 16 interactive controls between them — cannot be reached
by any means. The "View details" item in the catalog row menu navigates and silently does nothing,
which reads to an operator as a broken click.

**Repro.** Navigate to `/admin/catalog/019fe1af-7322-7c51-89ae-5660d996474d`. Observe `h1 = "Catalog"`
and the list table.

**Files.** `Frontend/src/routes/admin.users.tsx`, `admin.catalog.tsx`,
`admin.users.$userId.tsx`, `admin.catalog.$itemId.tsx`

---

### GAP-02 · Four of six feature flags are enforced nowhere

The Settings page presents platform-wide toggles for **Podcasts**, **Events & ticketing**,
**Tipping** and **Fan messaging**. Turning them off persists to `feature_flag` and changes nothing
about how the platform behaves.

`FeatureKey.PODCASTS`, `EVENTS`, `TIPPING` and `FAN_MESSAGING` appear in the settings read/write path
and nowhere else in the codebase. Only `ARTIST_SIGNUPS` (checked in `UpgradeToArtistService`) and
`PSP_REDDE` (checked in `PaymentGatewayRouter`) are actually consulted.

**Verified empirically**, not just by grep: with `podcasts=false` and `events=false` committed to the
database, `GET /v1/podcasts` and `GET /v1/events` both still returned `200` with full payloads.

**Impact.** This is the "supposed to apply system-wide" gap. An operator disabling Tipping or
Podcasts before a launch or during an incident would believe the feature is off. It is not. There is
no kill switch.

**Files.** `platform/domain/FeatureKey.java`;
`platform/adapter/in/rest/PlatformEnforcementFilter.java` (enforces maintenance mode only —
no flag checks)

---

### GAP-03 · The audit log does not show who did anything

The page's own subtitle reads *"Every privileged admin action, with actor and time."* It renders:

```
019fe17e-182a-7464-a5b3-bfb02c819ce0 · SUBMIT_RELEASE · Release:019fe1af-7322-7c51-89ae-5660d996474d
```

`audit_entry.actor_name` is **null on every row the application writes** — the column is populated
only by the row I inserted manually. Targets are raw ids with no title.

**Impact.** The audit trail is the control that makes every other admin power accountable, and it is
currently unreadable without a database session to resolve each UUID by hand. For a platform moving
money this is a compliance problem, not a cosmetic one.

**Repro.** `/admin/audit` with any activity in the log.

---

### GAP-04 · System health is entirely fabricated

`/admin/health` shows a green **"All systems normal"**. `GetHealthService` is:

```java
return new HealthView("normal", List.of(), List.of(), List.of());
```

Hardcoded status, three empty lists. There is no APM, no incident tracking, no gateway probe, no
listener telemetry anywhere in the codebase.

To be fair to whoever wrote it, the class documents this honestly and the UI does say "No service
metrics yet" beneath. But the headline an operator reads is a green all-clear that is true only in
the sense that the endpoint answered. **It will read "All systems normal" during a total payment
outage.**

**Impact.** Worse than having no health page, because it manufactures false confidence.

---

## 3. High — operationally dangerous

### GAP-05 · Takedown and flag fire instantly, with a canned reason

From the catalog list, **Take down** executes on a single click. No confirmation dialog, no reason
prompt. The reason is hardcoded:

```js
apiTakedownCatalog(c.id, 'Taken down by moderator (quick action from catalog list)')
```

**Flag** is worse — it calls `apiFlagCatalog(c.id)` with no note at all.

**Impact.** Pulling an artist's release from the store is among the most consequential actions in the
console, it is one misclick away, and the permanent audit record of *why* is a string that says
nothing. Compounded by GAP-06.

**File.** `Frontend/src/routes/admin.catalog.tsx:62-69`

---

### GAP-06 · Takedown is a one-way door in the UI

`POST /v1/admin/catalog/{id}/reinstate` exists, is guarded, and works. **No UI calls it.** Once a
release is taken down, there is no way to restore it from the admin console.

---

### GAP-07 · Settings reads lag one write behind for ~30 seconds

After `PUT /v1/admin/settings`, a subsequent `GET` returns the *previous* values for up to 30
seconds, then converges to the truth.

Observed: wrote `podcasts:false` → GET returned `podcasts:true`. Wrote the original values back →
GET returned `podcasts:false`. Minutes later all reads matched the database.

`FeatureFlagsAdapter` holds a 30s TTL cache and clears it on `set()`. The likely mechanism is that
invalidation happens *inside* the write transaction, so a read arriving before commit repopulates the
cache from pre-commit state and then serves it for the full TTL. **I did not confirm the mechanism —
only the behaviour.**

**Impact.** An admin toggles a flag, the page refetches, and shows the old value. The natural
response is to toggle again — which now writes the wrong thing. Ruled out browser HTTP caching
(`cache:'no-store'` and cache-busted query both behaved identically).

**File.** `platform/adapter/out/persistence/FeatureFlagsAdapter.java:24-65`

---

### GAP-08 · Three built endpoints have no UI at all

| Endpoint | Consequence |
|---|---|
| `POST /users/{id}/data-export` | GDPR/DSAR export unreachable — and the Compliance page exists to service exactly these requests |
| `POST /users/{id}/impersonate` | Support cannot reproduce a user's state |
| `GET /support/tickets/{id}` | Ticket detail; inbox says "Select a ticket." but no drill-in exists |

---

## 4. Medium

### GAP-09 · Settings silently accepts unknown flag keys
`PUT /v1/admin/settings` with `flags: { PODCASTS: false }` (enum-case instead of the wire's camelCase
`podcasts`) returns **200 OK** and changes nothing. A typo'd or renamed key is indistinguishable from
success. Should be `422` on unrecognised keys.

### GAP-10 · Admin users are listed as "Fan"
`/admin/users` shows `admin@beatzclik.com` with **ROLE = Fan**. The list derives role from
`is_artist` only and ignores `admin_member`. Admins are invisible as admins on the one screen that
enumerates accounts.

### GAP-11 · `GET /v1/admin/taxonomy` returns 422 without a `kind`
There is no "list all terms" for admins; the bare call is a validation error rather than the full
set. Forces the page into one request per kind and makes the endpoint feel broken when probed.

### GAP-12 · Admin catalog rows carry no artist name
`GET /v1/admin/catalog` items have no `artistName` field. A moderator approving or taking down a
release cannot see whose release it is from the list.

### GAP-17 · Every admin role can act on support tickets
Building the role matrix surfaced this: `assign`, `reply` and `resolve` on
`/v1/admin/support/tickets/{id}` permit **all five roles**, so a `finance` or `editor` admin can
reply to a fan on the platform's behalf. Contrast with catalog, where `support` may read but only
`moderator` may act. This may well be deliberate — everyone pitches in on support — but it is the
loosest grant in the console and it is the one that speaks to users in the platform's voice. Worth an
explicit decision rather than an inherited default.

*Not a defect: the implementation matches the annotation, and the matrix test asserts the current
intent. Flagged as a design question.*

### GAP-13 · Payment providers section is decorative
Settings renders MTN MoMo / Vodafone Cash / AirtelTigo / Card / Bank transfer with the caption
*"Not yet configurable — every method is currently enabled platform-wide."* Honest, but it is UI that
cannot do the thing it depicts.

---

## 5. Low

- **GAP-14** — Editorial shows a hardcoded `"Drag to reorder · live in 2h"`. The "2h" is a literal.
- **GAP-15** — `Export` buttons on Audit and Users were not exercised (see §7).
- **GAP-16** — "Joined" renders as `Aug 2026` with no day; ambiguous for support work.

---

## 6. System-wide observations

**Maintenance mode is the one platform control that genuinely works.**
`PlatformEnforcementFilter` blocks non-admin writes when enabled and correctly exempts `/v1/admin` so
an operator can turn it back off. This is the pattern the feature flags in GAP-02 should follow —
the enforcement point already exists; four flags simply aren't wired into it.

**Empty states are consistently honest.** Every page renders "No X yet" rather than inventing
figures, and Trust & safety explicitly labels unmeasured KPIs `— not measured yet`. This is a real
strength and directly contradicts the older pattern of rendering invented totals over an empty
database. GAP-04 is the one place this discipline breaks.

**The console is read-heavy and action-light in practice.** Of 61 endpoints, the destructive ones are
reachable in one click (GAP-05) while the corrective ones are unreachable entirely (GAP-06, GAP-08).
The balance is backwards.

---

## 7. What I could not test

Listed so this report is not mistaken for full coverage.

1. ~~**Non-super-admin roles.**~~ **CLOSED 2026-08-08 — and the original claim was wrong.**

   This section first said the four non-super-admin roles were "verified only by reading
   `@RolesAllowed`, never exercised with a real token." That was incorrect: 63 `403` assertions
   already existed across ten IT files, collectively touching all five roles.

   The real gap was narrower and different in kind — coverage was **uneven, and no test asserted the
   matrix as a whole**. `AdminRiskResourceIT` exercised only `moderator`; `AdminModerationResourceIT`
   carried one 403 assertion; `AdminCatalogResourceIT` never checked that `finance` or `editor` are
   refused. A widened annotation — `moderator` quietly becoming `moderator, editor` — could not have
   failed any test.

   Closed by `AdminRoleMatrixIT` (61 endpoints × 5 roles = 305 assertions, plus 61 asserting a plain
   fan token is refused everywhere). It passes: **no authorization defects exist.** The expected
   matrix is transcribed by hand rather than read from the annotations, so it is an independent
   statement of intent rather than a reflection check.

   Verified by mutation: temporarily declaring `finance` permitted on `GET /v1/admin/audit` produced
   exactly one failure — `DENIED but should be allowed: GET /v1/admin/audit as finance` — and nothing
   else. The test can fail, and fails precisely.
2. **Data-heavy behaviour.** Empty queues meant no pagination at volume, no sort/filter under load,
   no bulk-select across pages, no moderation triage, no dispute or payout flows.
3. **Export buttons** on Audit and Users — not clicked; unknown whether they produce a file, call an
   endpoint, or do nothing. No export endpoint appears in the OpenAPI spec, which is suggestive.
4. **`run-weekly` payouts and `disputes/*` actions** — deliberately not fired against real data on a
   database holding a live release and real accounts.
5. **Impersonation** — no UI, and not exercised via API.
6. **Overview "Needs attention"** — was empty throughout, but the catalog was empty of pending items
   the whole time, so I cannot say whether it would populate.

---

## 8. Suggested triage order

1. **GAP-01** — dead detail routes. Smallest fix, largest surface restored.
2. **GAP-02** — wire the four flags into `PlatformEnforcementFilter`. No kill switch is a launch risk.
3. **GAP-03** — populate `actor_name`. Compliance-relevant and cheap.
4. **GAP-05 + GAP-06** — confirmation + reason prompt on takedown, and expose reinstate. Ship together.
5. **GAP-07** — settings cache invalidation after commit.
6. **GAP-04** — either wire real health signals or stop showing a green all-clear.

---

*Endpoint sweeps were run from inside the authenticated browser session so the admin token was never
extracted. All mutation probes used a deliberately non-existent id; the only real writes were two
settings toggles, both reverted — `feature_flag` and `platform_settings` were verified back at their
original values.*
