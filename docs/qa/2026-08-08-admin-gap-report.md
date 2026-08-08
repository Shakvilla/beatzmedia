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

## 1b. Second pass — clicking every control (2026-08-08, later)

The first pass verified that pages *render* and endpoints *respond*. It did not verify that clicking
a control does what it says. This pass did: every action was clicked in the browser, and the result
checked against the **database**, not the toast.

That distinction found **GAP-19**, the worst finding in this report, which no amount of endpoint
testing would have surfaced.

**Verified working end to end** — clicked in the UI, confirmed in Postgres, audit entry present:

| Action | Evidence |
|---|---|
| Moderation: review, approve & keep | `qa-mod-2` → `in_review` → `resolved`; audit `Reviewed report`, `Approved content` |
| Trust: review, clear | `qa-risk-1` → `cleared`; audit `Reviewed risk signal`, `Cleared risk signal` |
| Support: reply | `support_message` row written, author `Admin`; ticket → `pending` |
| Compliance: start | `qa-cmp-3` → `in_progress`; audited |
| Taxonomy: create / rename / hide / delete | all four persisted, all four audited; hide correctly drops the term from the **public** `/v1/taxonomy` list |
| Settings: save | `FAN_MESSAGING` flipped in `feature_flag` and persisted |
| Catalog: takedown | release → `takedown`, fan `/v1/home` `newReleases` → 0, audited |
| Catalog: tab filters | pending/published/takedown/all each filter correctly |

**Three false alarms I caught before reporting them**, recorded because they show where this kind of
testing misleads:

1. Coordinate-based clicks silently failed several times (the screenshot is 2× the coordinate
   space). "Review does nothing" and "Published tab is empty" were both my harness, not the app.
   Both were confirmed working once driven through the DOM.
2. "Reply has no send button" — it has one; it is an icon button with `aria-label="Send"` and no text,
   which my text scan missed.
3. "Rename doesn't persist" — React listens for `focusout`, not the non-bubbling `blur` I dispatched.
   It persists correctly with a real focus/blur.

---

## 2. Blockers — do not ship

### GAP-19 · Seven buttons do nothing but claim they worked

Found only by clicking. Each of these has an `onClick` that fires a toast and makes **no API call
whatsoever** — verified in the browser: zero network requests, no download, no navigation.

| Control | File | What it claims | Severity |
|---|---|---|---|
| **Export** (audit log) | `admin.audit.tsx:56` | `"Exporting audit log as CSV"` — **success** | High |
| **Export** (users) | `admin.users.tsx:90` | `"Exporting users as CSV"` — **success** | High |
| **Export** (ledger) | `admin.finance.ledger.tsx:55` | `"Exporting ledger as CSV"` — **success** | High |
| **Sign out** (device) | `admin.users.$userId.tsx:169` | `"Signed out of device"` — **success** | High |
| **New playlist** | `admin.editorial.tsx:78` | `"New playlist — pick tracks to curate"` | High |
| **Schedule push** | `admin.editorial.tsx:122` | `"Schedule a new push notification"` | High |
| **Preview track** | `admin.catalog.$itemId.tsx:102` | `"Previewing …"` | Low |

Four of them report **`'success'`**. This is worse than a dead button: a dead button is discovered
the first time someone uses it, whereas a button that reports success is believed.

Three consequences worth separating:

- **"Signed out of device" is a security control.** An operator responding to a compromised account
  is told the session was terminated. It was not. Nothing was called.
- **Editorial is entirely non-functional**, and not for want of a backend:
  `POST /v1/admin/editorial/playlists` and `POST /v1/admin/editorial/push` both exist, are guarded,
  and work. The page has two buttons and neither is connected to either endpoint. Verified live:
  clicking both produced **0** API calls.
- **The three CSV exports have no endpoint at all** — nothing in the OpenAPI spec serves them. This
  is unbuilt backend, not just unwired frontend, so the fix is larger than connecting a handler.

**Repro.** Open `/admin/audit`, click **Export**. A green success toast appears. No file is
downloaded and the network tab stays silent.

---

### GAP-25 · Every track displays invented lyrics

`GET /v1/tracks/{id}/lyrics` exists in the API. `lyrics-view.tsx:9` does not call it — it imports
`getLyrics()` from `src/lib/lyrics-data.ts`:

```ts
export function getLyrics(trackId: string, duration: number): LyricLine[] {
  if (SPECIFIC[trackId]) return SPECIFIC[trackId]
  const n = GENERIC.length
  return GENERIC.map((text, i) => ({ time: Math.round((i / n) * duration), text }))
}
```

For any track without a hardcoded entry — i.e. **every track a real artist uploads** — it returns a
generic block of placeholder lines, evenly spread across the song's duration so it looks
time-synced.

Unlike an empty rail, this does not read as missing data. It reads as *this artist's lyrics*.
Fabricated words attributed to a named artist's song is a different kind of problem from a broken
button, and it ships to fans. The backend already has `lyrics` and `lyric_line` tables and a working
endpoint.

Related: the **Share** button on the same view reports "Lyric link copied" without touching the
clipboard (GAP-21).

---

### GAP-24 · The ⌘K command palette searches hardcoded fiction

`admin-command.tsx:5` imports `getAdminUsers()` and `getCatalog()` from `src/lib/admin-data.ts` —
**hardcoded arrays**, not the API queries every other admin screen uses.

Demonstrated live against a database holding exactly 2 accounts and 1 release:

| Query | Palette returns | Reality |
|---|---|---|
| `Kojo` | *Kojo Asante*, *DJ Kojo* | Neither account exists |
| `Iron Boy` | *Iron Boy · Black Sherif* | Deleted; no such release |
| `Abdul` | **"No results for 'Abdul'."** | A real, active artist account |

So the one search box spanning the whole console **invents users and releases that do not exist and
cannot find the ones that do.**

It compounds with GAP-01: selecting a fabricated result navigates to
`/admin/users/u-kojo` or `/admin/catalog/c1`, and because those detail routes are dead, the operator
lands silently back on the list — no error, no "not found", just a click that appears to do nothing.

This is the same "invented data over working endpoints" class that commit `1cbfdc8` set out to
remove. It survived because the palette is a shared component rather than a route, so a
route-by-route sweep misses it.

**I swept for the rest.** 22 files import from the mock modules, but almost all import *types*
only, which is harmless. Exactly **three** import data-returning functions:

| File | Imports | Status |
|---|---|---|
| `components/admin/admin-command.tsx` | `getAdminUsers`, `getCatalog` | This finding |
| `components/music/lyrics-view.tsx` | `getLyrics` | GAP-25 |
| `routes/admin.users.$userId.tsx` | `getUserDetail` | Fabricated activity feed — "Bought 'Soja' · ₵2.50", "Followed Black Sherif" — for whichever user is opened. Currently masked by GAP-01 (the route is dead); **fixing GAP-01 alone would expose invented per-user activity**, so the two must be fixed together. |

The type-only imports are worth leaving alone; the distinction matters because a naive "delete the
mock modules" would break 22 files for a problem that lives in three.

---

### GAP-23 · No password-reset email is ever sent, in any environment

Reached by asking a question the endpoint sweep could not: after inviting an admin, **how does that
person ever sign in?**

The invite works — it creates a stub account with the right role and `is_admin = true`. But the stub
has **no credential**, and:

- signing up with that email returns `409 EMAIL_TAKEN`;
- `InviteAdminService` sends nothing — there is no invite email and no accept/activate endpoint;
- the only recovery path is the public `POST /v1/me/password/reset`, which does correctly mint a
  `password_reset_token` row.

That token never reaches the user. The identity module's only `Mailer` implementation is
`LoggingMailer`, which logs and returns:

```java
LOG.infof("Password reset requested for %s; reset token generated and dispatched.", email);
```

It is `@ApplicationScoped` with **no profile guard** — not `@IfBuildProfile("dev")`, no `%prod`
alternative. It is the implementation in production too. The class javadoc is explicit that it "must
be swapped for a real SMTP/provider adapter before go-live"; that swap has not happened, and nothing
fails a build or a test if it never does.

**Impact, in order of severity:**

1. **No user can recover a forgotten password.** Not admins, not artists, not fans. The reset token
   is created and silently discarded.
2. **Every invited admin is locked out permanently** — no password, no signup, no email.
3. The log line says **"dispatched"**, so operations sees a healthy-looking success message for mail
   that was never sent. Same shape as GAP-19, one layer down.

Note the platform *can* send mail: `notifications` has a real `SmtpMailer`, and Mailpit is running in
Compose on 1025/8025. Identity simply does not use it — there are two separate `Mailer` ports and
only one of them is implemented for real.

**Repro.** `POST /v1/me/password/reset` with any registered email → `204`. A `password_reset_token`
row appears. The mail sink at `http://localhost:8025` stays empty.

---

### GAP-22 · Nothing in the application ever creates an album

Found by following a published release all the way to the fan app rather than stopping at "the
endpoint returned 200".

`release` supports four types — `single | ep | album | mixtape` — and publishing any of them writes
`release`, `release_track` and `track` rows. **It never writes an `album` row.** The only
`INSERT INTO album` in the entire codebase is in `R__seed_dev_data.sql`.

So the `album` table can only ever contain dev-seed data. **In production it will be empty forever**,
and everything keyed on it is dead:

| Surface | Consequence |
|---|---|
| `rails.newReleases` (`newestAlbums()`) | Permanently empty — nothing an artist releases ever appears in "New releases" |
| `featuredAlbums` | Permanently empty |
| `GET /v1/albums/{id}` | No real album can be fetched |
| `GET /v1/artists/{id}/albums` | Always empty on an artist's page |
| `PUT /v1/me/saved/albums/{albumId}` | Nothing real to save |
| `Frontend/src/routes/album/` | An entire detail route with no reachable content |

**Verified live.** The release "Test" is `live` and genuinely fan-visible — `trending: 1`,
`top10: 1`, `popularArtists: 1`, `/artists/{id}/tracks: 1`, `/search?q=Sweet: 1`. Only the
album-backed rails are empty: `newReleases: 0`, `featuredAlbums: 0`, with `album` at 0 rows.

**Why this is easy to miss.** Every individual endpoint answers `200`, the release genuinely reaches
fans through five other surfaces, and the empty rails read as "no data yet" on a fresh database. The
defect is only visible when you ask *which* table the rail reads and then check whether anything
writes to it.

**Note on INV-2.** "Album purchases expand to all constituent tracks" is a stated invariant. Album
purchase is keyed on an entity the product cannot create, so that invariant is currently untestable
against real content.

---

### GAP-21 · The same anti-pattern runs through Studio and the fan app — 14 controls total

GAP-19 is not an admin problem. Sweeping the whole frontend for the same shape found **14
toast-only controls across 10 files**, **9 of which report `'success'`**. There are no other
dead-control patterns — no empty handlers, no `alert()` stubs — so this is one anti-pattern applied
consistently, which makes it both easy to find and easy to fix.

**Creator-facing (Studio) — 6:**

| Control | File | Claims | Why it matters |
|---|---|---|---|
| Change password | `studio.settings.tsx:252` | **"Password reset link sent to your email"** — success | No email is sent. A locked-out creator waits for a mail that never arrives. |
| Deactivate profile | `studio.settings.tsx:413` | "Profile deactivated — reactivate any time" | The profile stays fully live. The creator believes they are offline. |
| Send thank-you | `studio.audience.tsx:91` | **"Thank-you sent to your top superfans 💚"** — success | Nothing is sent to anyone. The creator believes they contacted their fans. |
| Export transactions | `studio.payouts.tsx:162` | "Exporting transactions as CSV" — success | Financial records. |
| View invoices | `studio.settings.tsx:401` | "Exporting invoices" — success | Financial records. |
| Manage billing | `studio.settings.tsx:398` | "Opening billing portal" | No portal opens. |

**Fan-facing — 1:**

| Control | File | Claims | Why it matters |
|---|---|---|---|
| Share lyrics | `lyrics-view.tsx:69` | **"Lyric link copied"** — success | The clipboard is never written. The fan pastes whatever was there before. |

**The three that assert a security, account or communication outcome are the sharpest** — password
reset, profile deactivation, and the fan thank-you. Each tells a user that something irreversible or
outbound has happened on their behalf. None of it has.

---

### GAP-20 · `overdue` compliance requests can never appear

`ComplianceStatus.OVERDUE` exists, the UI counts it (`admin.compliance.tsx:60`) and styles it red —
but **nothing ever sets it**. The domain class says so outright: *"no scheduler recomputes it in this
WU."* There is no sweep job.

Confirmed live: a compliance request seeded a day past its `due_at` still displayed under
`0 overdue`.

**Impact.** DSAR deadlines are statutory. The one indicator that a legal deadline has been missed is
permanently stuck at zero, on the page whose entire purpose is tracking those deadlines.

---

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

0. **GAP-23** — no password-reset mail is sent anywhere. Nobody can recover an account, and every
   invited admin is locked out. `notifications.SmtpMailer` already works and Mailpit is running, so
   this is wiring identity's `Mailer` port to a real adapter, not building one. Highest
   severity-to-effort ratio in this report.
0. **GAP-22** — nothing creates albums. Six fan-facing surfaces, including the "New releases" rail
   and the entire album detail route, are dead on any database without the dev seed. Decide whether
   publishing an `album`/`ep`/`mixtape` release should project an `album` row (the same projection
   shape already used for podcasts), or whether the album concept should be retired in favour of
   releases. Everything else in this list is a defect; this one is a missing feature the rest of the
   product already assumes exists.
1. **GAP-19 + GAP-21** — 14 controls that do nothing, 9 claiming success, across admin, Studio and
   the fan app. Fix the two editorial buttons by connecting them (backends exist). For the rest,
   the honest interim move is to **remove or visibly disable** them: a missing button costs a
   feature, a button that says "Password reset link sent" costs trust. Nothing should ship
   reporting success while doing nothing.
2. **GAP-01** — dead detail routes. Smallest fix, largest surface restored.
3. **GAP-02** — wire the four flags into `PlatformEnforcementFilter`. No kill switch is a launch risk.
4. **GAP-03** — populate `actor_name`. Compliance-relevant and cheap. Note `support_message` already
   stores an author name, so the pattern exists.
5. **GAP-05 + GAP-06** — confirmation + reason prompt on takedown, and expose reinstate. Ship
   together: takedown is one click, irreversible from the UI, and stamps a canned reason into the
   permanent audit record. Confirmed live.
6. **GAP-20** — either compute `overdue` from `due_at` or add the sweep. Statutory deadlines.
7. **GAP-07** — settings cache invalidation after commit. Observed biting in the UI: a second toggle
   after a save operates on stale state and writes the wrong value.
8. **GAP-04** — either wire real health signals or stop showing a green all-clear.

---

*Endpoint sweeps were run from inside the authenticated browser session so the admin token was never
extracted. All mutation probes used a deliberately non-existent id; the only real writes were two
settings toggles, both reverted — `feature_flag` and `platform_settings` were verified back at their
original values.*
