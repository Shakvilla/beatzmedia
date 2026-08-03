# Issue Register

Ordered by user impact. Every issue was observed in the browser first; root causes were
traced afterwards. Severity is about **what it does to a real user or to trust in the data**,
not about how hard it is to fix.

| ID | Severity | Area | Summary |
|---|---|---|---|
| [I-1](#i-1) | **Critical** | Studio | Overview KPIs are mock data; contradict Payouts one click away |
| [I-2](#i-2) | **Critical** | Checkout | Unpaid order polls forever — no timeout, no cancel, no failure state |
| [I-3](#i-3) | **High** | Admin | Every admin is shown as "Admin · Yaa" with a **SUPER-ADMIN** badge |
| [I-4](#i-4) | **High** | Checkout | A MoMo number the user never added is shown as their saved default |
| [I-5](#i-5) | **High** | Auth | 15-minute session, no refresh — silent logout with no message, no return path |
| [I-6](#i-6) | **Medium** | Settings | Fan settings shows a hardcoded "₵312 spent" |
| [I-7](#i-7) | **Medium** | Auth | Malformed email (HTTP 422) is reported as "Incorrect email or password." |
| [I-8](#i-8) | **Medium** | Admin | User detail page is entirely mock: activity, orders, devices, spend |
| [I-9](#i-9) | **Medium** | Admin | Catalog totals are hardcoded ("1,260 artists · 18,420 albums") |
| [I-10](#i-10) | **Low** | Auth | "Forgot password?" links to `/login` — it does nothing |
| [I-11](#i-11) | **Low** | Global | Browser tab title is `temp-app` |
| [I-20](#i-20) | **Critical** | Catalog | One missing lyrics file 404s the whole track page — only 1 song is openable |
| [I-27](#i-27) | **Critical** | Studio | "Set up artist studio" crashes — stale token, no new JWT issued |
| [I-12](#i-12) | **Critical** | Player | No audio ever plays — the player is a simulated timer |
| [I-14](#i-14) | **Critical** | Tips | "₵5 tipped via MoMo" with zero network calls; no money moves |
| [I-13](#i-13) | **High** | Catalog | Tracks the user does not own are badged **OWNED** (search, album, track) |
| [I-21](#i-21) | **High** | Access | Nothing is visible without an account — shared links hit a login wall |
| [I-22](#i-22) | **Medium** | Onboarding | No onboarding; nothing is ever asked of a new user |
| [I-23](#i-23) | **Medium** | Media | `/images/placeholder.jpg` missing — 8/49 home images render blank |
| [I-24](#i-24) | **Medium** | Home | "Made for you" with no taste data; player pre-loaded with an unchosen song |
| [I-25](#i-25) | **Medium** | Commerce | "Buy rest ₵7.50" costs more than "Buy album ₵6.00" |
| [I-28](#i-28) | **Medium** | Admin | Overview says "nothing needs attention" while 2 items sit in review; blank GMV card |
| [I-29](#i-29) | **Medium** | Admin | User list cannot show who is an admin (role is fan/artist only) |
| [I-26](#i-26) | **Low** | Search | Playlists tab shows nothing and no empty state |
| [I-16](#i-16) | **Medium** | Admin | Pending payout row shows a raw account UUID instead of the artist |
| [I-17](#i-17) | **Medium** | Admin | Health says "All systems normal" while reporting no metrics |
| [I-18](#i-18) | **High** | Studio | New artist cannot save profile; error never names the required field |
| [I-19](#i-19) | **High** | Admin | "Approve" on the catalog queue always fails (draft listed as pending) |
| [I-15](#i-15) | **Low** | Studio | Profile avatar shows `?` when display name is blank (regression from PR #178) |

---

## I-1
### Studio overview KPIs are mock data and contradict the real pages they link to

**Severity: Critical** — an artist cannot tell what they have actually earned.

**Steps**
1. Log in as the artist account (owns **zero** releases).
2. Go to `/studio`.
3. Read the KPI row.
4. Click the "This month" card, which navigates to `/studio/payouts`.

**Expected** — a new artist with no releases sees zeros everywhere.

**Actual**

| Screen | "This month" | Streams | Monthly listeners | Available balance |
|---|---|---|---|---|
| `/studio` | **₵21,680 (+24%)** | **412K (+18%)** | **2.4M (+18%)** | ₵0.00 |
| `/studio/payouts` | **₵0 (0%)** | — | — | ₵0.00 |

The backend agrees with Payouts, not with the Overview:

```
GET /v1/studio/payouts   → {"available":0.00,"thisMonth":0.00,"lifetime":0.00,"transactions":[]}
GET /v1/studio/analytics → {"fans":0,"engagement":{"completion":0,"save":0,"skip":0},"countries":[]}
GET /v1/studio/releases  → {"items":[],"total":0}
```

**Root cause** — `Frontend/src/routes/studio.index.tsx:35-39`. Three mock getters sit
directly above the one real query, and their results are rendered side by side in the
same KPI row:

```ts
const analytics    = getAnalytics('28d')   // mock — lib/studio-analytics.ts:114
const audience     = getAudience('28d')    // mock — lib/studio-analytics.ts:198
const payoutStats  = getPayouts()          // mock — lib/studio-payouts.ts:60
const { data: payouts } = useQuery(payoutsQuery())   // real
const balance = payouts?.available ?? 0              // real
```

`studioAnalyticsQuery`, `studioAudienceQuery` and `payoutsQuery` all already exist and are
used correctly by `/studio/analytics`, `/studio/audience` and `/studio/payouts`. Only the
overview was left on mocks.

**Why this is the worst one:** the fake and real numbers are *adjacent*. "₵21,680 earned"
beside "₵0.00 available" reads as a payout delay, not as fiction. An artist would reasonably
believe they are owed ₵21,680.

**Fix** — swap the three getters for the existing queries and delete the mock modules'
getters. Expect the dashboard to go to zeros for new artists; that is correct.

---

## I-2
### An unpaid order polls forever with no timeout, no cancel and no failure state

**Severity: Critical** — the user is stranded on a payment screen, uncertain whether they
have been charged.

**Steps**
1. As the fan, add any store item to the cart.
2. Checkout → "Pay ₵800.50 with MoMo".
3. Do not approve anything (no MoMo provider exists in dev — this is also what a real
   declined or expired prompt looks like).
4. Wait.

**Expected** — after a bounded wait, a timeout or failure state with a way out
("Payment not confirmed — retry / cancel / contact support").

**Actual** — the page sits on *"Authorizing on your phone… Approve the MoMo PIN prompt"*
indefinitely. Observed **30+ poll requests over ~60 seconds** and still going, with no
change in the UI. There is no cancel button, no elapsed-time message, and no way back
except the browser Back button. The cart badge still reads **1**, so the item is still in
the cart while an order is in flight.

```
GET /v1/me/orders/019fba83-…  → 200   (×30+, every 2s, unbounded)
```

**Root cause** — `Frontend/src/routes/checkout.complete.tsx:24-27`:

```ts
refetchInterval: (query) => {
  const status = query.state.data?.status
  return status === 'pending' ? 2000 : false
},
```

The route does handle `failed` (line 62) and network `isError` (line 39). The gap is
specifically **`pending` that never resolves** — which is exactly what a real expired or
ignored MoMo prompt produces, since those typically lapse after ~60 seconds.

**Fix** — cap the polling (e.g. stop after 90–120s) and render a distinct
"we haven't had confirmation yet" state with retry and support paths. Decide explicitly
whether the cart should be held or cleared while an order is pending.

---

## I-3
### Every admin is displayed as "Admin · Yaa" with a SUPER-ADMIN badge

**Severity: High** — the sidebar asserts a name and a privilege level it never checked.

**Steps**
1. Log in as `qa.admin@beatzclik.local` (account name: **QA Admin**).
2. Go to `/admin` and look at the sidebar footer.

**Expected** — the signed-in admin's own name and their actual role.

**Actual** — **"AD · Admin · Yaa"** with a green **SUPER-ADMIN** shield, for any admin who
signs in. The role badge is not read from the session at all, so a `support` or `moderator`
member would also be labelled SUPER-ADMIN.

**Root cause** — `Frontend/src/lib/admin-data.ts:15-19` and
`Frontend/src/components/admin/admin-shell.tsx:66-71`:

```ts
export const adminUser: AdminUser = { name: 'Yaa', role: 'Super-admin', initials: 'AD' }
```

**This is the same bug class as `studioArtist`**, fixed for Studio in PR #178 by
`features/studio/use-creator-identity`. The admin shell needs the same treatment: name and
initials from the session, role from the admin member record.

---

## I-4
### Checkout shows a saved MoMo number the user never added

**Severity: High** — on the payment screen, which is the worst place to show invented data.

**Steps**
1. As the fan (a fresh account with no saved payment methods), go to `/checkout` with an
   item in the cart.

**Actual** — the payment list shows **"MTN MoMo — 0244 ••• 9210 - default"**, and the
confirmation copy reads *"You'll receive a prompt on 0244 ••• 9210 to authorize this
payment."* This account has never registered a payment method.

**Root cause** — hardcoded in `Frontend/src/routes/checkout.index.tsx:15` and again in the
body copy at line 111.

**Fix** — read saved methods from the account. If there are none, the flow should collect a
number rather than implying one is on file.

---

## I-5
### Sessions expire after 15 minutes with no refresh, no warning and no return path

**Severity: High** — affects every user in every flow, repeatedly.

**Steps**
1. Log in.
2. Let the token expire (15 min), or invalidate it to simulate expiry.
3. Navigate anywhere, e.g. `/library`.

**Expected** — either a silent refresh, or a redirect to login that says *why* and returns
the user to where they were.

**Actual** — redirected to `/login` with no message. After logging back in the user lands on
`/`, not on the page they were trying to reach. Token lifetime is **900 seconds** and there
is no refresh-token mechanism: `Frontend/src/lib/api/client.ts:42-45` clears the token and
hands off to the unauthorized handler on any 401.

**Impact in practice** — a creator part-way through the release wizard, or a fan on the
checkout screen, loses their place every 15 minutes.

**Fix** — either issue refresh tokens or extend the TTL, and in both cases pass a `redirect`
param through the login round-trip and show "your session expired, please sign in again".

---

## I-6
### Fan settings shows a hardcoded "₵312 spent"

**Severity: Medium** — fabricated money figure.

**Actual** — `/settings` shows **`0 OWNED · 0 PLAYLISTS · ₵312 SPENT`** for an account that
has bought nothing. The row contradicts itself.

**Root cause** — `Frontend/src/routes/settings.tsx:84`:
`<Stat label="Spent" value="₵312" />`

---

## I-7
### A malformed email is reported as "Incorrect email or password."

**Severity: Medium** — sends the user to debug the wrong thing.

**Steps** — on `/login`, enter `not-an-email` and any password, submit.

**Actual** — `POST /v1/auth/login → 422 Unprocessable Entity` (the backend's message is
"A valid email address is required"), but the UI renders **"Incorrect email or password."**
The user will retype a password that was never the problem.

Related: the Log in button is correctly disabled while the form is empty, but it **enables
for `not-an-email`** — there is no client-side format check.

---

## I-8
### Admin user-detail page is entirely mock data

**Severity: Medium**

**Actual** — `/admin/users/:id` renders activity, orders and devices from
`getUserDetail()`, plus hardcoded stat values, regardless of which user is being viewed:

```ts
// routes/admin.users.$userId.tsx:28
const detail = useMemo(() => getUserDetail(), [])
// :69-70
[{ label: 'Releases', value: '12' }, { label: 'Revenue', value: '₵42K' }, { label: 'Followers', value: '412K' }]
[{ label: 'Purchases', … }, { label: 'Lifetime spend', value: '₵312' }, { label: 'Playlists', value: '7' }]
```

A support agent looking up a user is reading someone else's invented history.

---

## I-9
### Admin catalog totals are hardcoded

**Severity: Medium**

**Actual** — `/admin/catalog` states **"1,260 artists · 18,420 albums · 142,800 tracks"** as
fact. The dev catalogue is a handful of records.

**Root cause** — `Frontend/src/lib/admin-data.ts:488`:
`export const CATALOG_SUMMARY = { artists: 1260, albums: 18420, tracks: 142800 }`
rendered at `routes/admin.catalog.tsx:97`.

---

## I-10
### "Forgot password?" goes nowhere

**Severity: Low** — `href="/login"`, so the link reloads the page the user is already on.
There is no password-reset route. Either build it or remove the link.

---

## I-11
### Browser tab title is "temp-app"

**Severity: Low** — `document.title` is the scaffold default on every page. Visible in the
tab, in bookmarks, and in browser history.

---

## What is working

Worth recording, so effort goes where it is needed:

- **Login, logout and signup** all work correctly, including the wrong-password error.
- **Route protection** works — signed-out users are redirected to `/login`; the fan account
  is correctly refused Studio and Admin (`403`).
- **Cart and checkout arithmetic** is right: ₵800.00 + ₵0.50 service fee = ₵800.50, and the
  order is created server-side (`POST /v1/checkout → 202`) with correct line items.
- **Add to cart persists** to the backend (`POST /v1/me/cart/items → 200`), not just locally.
- **Admin overview is honest** — it shows `0` and `—` for streams and GMV on an empty
  platform instead of inventing plausible figures.
- **Studio creator identity** (PR #178) is confirmed fixed in the running UI: the artist sees
  "Ama Serwaa", not Black Sherif.
- **Fan pages render without errors**: home, library, search, store, podcasts, events,
  notifications, cart, checkout.

---

## I-12
### No audio ever plays — the player is a simulated timer

**Severity: Critical** — this is a music streaming product and it does not play music.

**Steps**
1. Sign in as the fan, open any track (e.g. `/track/last-last`).
2. Press play on the player bar.
3. Listen.

**Expected** — audio.

**Actual** — silence. The player bar behaves convincingly: it shows the track, artist,
album, duration, and the elapsed counter advances (observed **0:30 → 0:33 over 3 seconds**).
But there is **no `<audio>` element on the page at any point** — before play, after play,
anywhere in the fan app.

```js
document.querySelector('audio')   // null, before and after pressing play
```

**Root cause** — playback was never implemented. `features/player/player-context.tsx` manages
play/pause, progress, volume and queue as pure React state, and its own header comment
describes the intended wiring in the conditional:

> "…from an `<audio>` element's timeupdate event and dispatch the same actions —"

That element does not exist. The only `<audio>` tags in the codebase are in Studio, for
local file previews during upload (`studio.release.new.tracks.tsx:55`,
`studio.podcasts.new.tsx:106`).

**Knock-on effects**
- **INV-3 (server-enforced 30-second preview for unowned for-sale tracks) cannot be
  exercised at all** from the UI. The scrub-cap logic in `player-bar.tsx:47` is dead code
  until there is real audio.
- Every "Play" affordance across Home, Search, Library, Store, Album, Artist and Playlist
  pages is non-functional.

**Fix** — this is a feature to build, not a bug to patch. Until then the play controls are
misleading: they animate as though something is happening.

---

## I-13
### A track the user does not own is badged "OWNED"

**Severity: High** — "owned" is the central promise of a buy-to-own product.

**Steps**
1. As the fan (owns nothing), search for `burna`.
2. Look at "Last Last" in the results.

**Actual** — badged **OWNED**, while `/library` for the same account says
**"Owned Tracks · 0 tracks"**. The two screens contradict each other.

**This one is server-side.** The frontend renders faithfully — `mappers.ts:102` passes the
field straight through, and `search.tsx:158` renders the badge on `ownership === 'owned'`:

```
GET /v1/search?q=burna   → { "title":"Last Last", "ownership":"owned", "price":null }
GET /v1/me/collection    → { "ownedTracks": [] }
```

The track has `price: null` (not for sale), and the search projection appears to map
"not for sale" to `owned` rather than to a free/streamable state.

**Fix** — decide the contract: either `ownership` gains a distinct value for
free/not-for-sale content, or search resolves real ownership per caller. Until then, search
tells fans they own things they have not bought, and `/me/collection` disagrees.

---

## I-14
### Tipping claims money was sent and sends nothing

**Severity: Critical** — a money feature that lies to both sides.

**Steps**
1. As the fan, open any artist page (e.g. `/artist/burna-boy`).
2. Click **₵ Tip artist**, choose ₵5, click **Send ₵5 with MoMo**.

**Expected** — a payment intent, a MoMo authorization, a ledger entry, and the artist
eventually receiving the tip.

**Actual** — a green toast: **"Thank you! ₵5 tipped to Burna Boy via MoMo 💚"**.
Instrumenting `window.fetch` across the click recorded **zero network requests**:

```js
{ "networkCallsDuringTip": [], "toast": "Thank you! ₵5 tipped to Burna Boy via MoMo 💚" }
```

No money moves. The artist is never told. The fan believes they have paid.

**Root cause** — the handler is a toast:
- `Frontend/src/routes/artist/$artistId.tsx:212`
- `Frontend/src/routes/podcast.$podcastId.tsx:202` (same problem for podcast tips)

**What makes this worse than a bug: the backend is already finished.**

```
payments/adapter/in/rest/TipResource.java        → @Path("/v1/payments/tips")
podcasts/adapter/in/rest/PodcastResource.java    → @Path("/podcasts/{id}/tip")
payments/application/service/IssueTipService.java
payments/application/service/TipLedgerPoster.java
payments/application/service/TipSettlementSubscriber.java
podcasts/domain/SelfTipNotAllowedException.java, TipsDisabledException.java
```

A complete tipping system — intents, ledger posting, settlement, self-tip and
tips-disabled rules — is built and unreferenced. `grep` for `payments/tips` under
`Frontend/src/lib/api/` returns nothing.

**Fix** — wire the existing endpoint. This is integration work, not new development. Until
it is wired, the toast must not claim success.

---

## I-15
### Studio profile avatar shows "?" when the display name is blank

**Severity: Low** — **a regression I introduced in PR #178.** Recording it against myself.

**Steps** — as an artist whose `displayName` is still empty, open `/studio/profile`.

**Actual** — the sidebar avatar correctly shows **AS** (derived from the account name), but
the profile page's own avatar shows **?**. Two different monograms for the same person on
one screen.

**Cause** — PR #178 changed the profile avatar to `initialsOf(p.displayName)`, and
`initialsOf` returns `'?'` for an empty string. Before the PR both showed `BS` — consistent,
but wrong for everyone who is not Black Sherif.

**Fix** — fall back to the session-derived initials:
`p.displayName.trim() ? initialsOf(p.displayName) : creator.initials`.

---

## I-16
### Admin payout row shows a raw account UUID instead of the artist

**Severity: Medium** — an operator is asked to approve a payment to an unidentifiable party.

**Actual** — on `/admin/finance`, the pending payouts table renders:

```
ARTIST                                   AMOUNT      METHOD          STATUS       ACTION
019f872e-7299-7e99-a08b-8b38f0e51879     ₵9,400.5    MoMo · Telecel  KYC pending  Send
```

The artist column shows an account UUID. **Send** moves real money, and the operator cannot
tell who they are paying.

Also on that row: **₵9,400.5** — the amount is not formatted to two decimals, unlike every
other money figure in the console (`₵9,400.50`).

---

## I-17
### Admin health reports "All systems normal" while stating it has no metrics

**Severity: Medium** — an all-clear that was never measured.

**Actual** — `/admin/health` renders, in order:

> **All systems normal**
> No service metrics yet.
> Concurrent listeners (24h) — No listener telemetry yet.
> Recent incidents — No incidents recorded.

The headline asserts a healthy platform; the three panels below admit there is no data
behind it. If a service were down, this page would still say "All systems normal", because
the banner is not derived from anything.

**Fix** — the banner should reflect measured state, or say "no health data" as the panels
already do. The Trust & safety page (`— not measured yet`) is the right pattern; Health
should match it.

---

## I-18
### A new artist cannot save their profile, and the error never says why

**Severity: High** — blocks the first thing an artist is asked to do.

**Steps**
1. Sign in as an artist whose profile has never been completed (`username` is `""`).
2. `/studio/profile` → type a Display name → **Save changes**.

**Expected** — saved, or a message naming the field that is wrong.

**Actual** — the toast reads **"Could not save your profile. Please try again."** The banner
stays on "Unsaved changes" and nothing persists. Retrying produces the same result forever.

The real reason, from the API:

```
PUT /v1/studio/profile
→ 422 {"error":{"code":"VALIDATION","message":"Username is required.","field":"username"}}
```

`username` is mandatory server-side, but the form does not mark it required, does not
validate it before submitting, and — although the response carries `field: "username"` —
the UI discards it and shows generic retry copy.

**Root cause** — `routes/studio.profile.tsx` maps only `USERNAME_TAKEN` and `INVALID_GENRE`
to specific messages; a plain `VALIDATION` falls through to the generic branch. The
response's `field` is never used.

**Fix** — mark Username required, block save while it is empty, and surface `error.field`
against the offending input.

---

## I-19
### Admin "Approve" on the catalog queue always fails

**Severity: High** — the console's primary catalog action does not work.

**Steps**
1. Sign in as admin → `/admin/catalog`.
2. The header reads "Pending review · 1". Click **Approve** on that row.

**Actual** — toast: **"Could not approve release"**. The item stays in the queue.

```
POST /v1/admin/catalog/019f8c16-…/approve
→ 409 {"code":"ILLEGAL_TRANSITION",
       "message":"Cannot apply 'APPROVE_IMMEDIATE' to a release in status 'draft'"}
```

The release is a **draft** — never submitted for review — yet it is listed in the
pending-review queue with an Approve button that can never succeed.

**Two things to settle**
1. Should `draft` releases appear in the pending-review queue at all? The count
   ("Pending review · 1") is counting something that is not pending review.
2. If they legitimately appear, the row must not offer an action that is invalid for its
   status.

Either way the error copy should say the release is not awaiting review, not "try again".

---

## I-13 (extended)
### The ownership mislabel is not limited to search

Same root cause, three more surfaces, all with the fan account that owns **nothing**:

| Screen | Claim |
|---|---|
| `/search?q=burna` | "Last Last" badged **OWNED** |
| `/album/iron-boy` | **"YOU OWN 2/6"** |
| `/track/last-last` | **"In your collection"** |
| `/library` | **"Owned Tracks · 0 tracks"** ← the truthful one |

Any screen reading `ownership` from the catalog projection inherits the error. Fixing the
contract fixes all of them at once.

---

## I-20
### One missing lyrics file 404s the entire track page

**Severity: Critical** — almost every song in the catalogue is unopenable.

**Steps**
1. Sign in as any fan, search for `burna`.
2. Click **For My Hand** (₵3.00) in the results.

**Actual** — a full-page **"Track not found / Back to home"**.

The track exists and loads fine. It is the *lyrics* sub-request that 404s, and the page treats
that as the track being missing:

```
GET /v1/tracks/for-my-hand          → 200 OK
GET /v1/tracks/for-my-hand/lyrics   → 404 Not Found      ← this kills the page
```

Across the catalogue:

| Track | `/tracks/:id` | `/tracks/:id/lyrics` | Detail page |
|---|---|---|---|
| Last Last | 200 | **200** | loads |
| For My Hand | 200 | 404 | **Track not found** |
| It's Plenty | 200 | 404 | **Track not found** |
| Calm Down | 200 | 404 | **Track not found** |

**Exactly one track has lyrics, so exactly one track has a working page.** Every route into a
song — search results, album track lists, playlists, the player — leads to "Track not found"
for everything else. Confirmed with a full page load, not just client-side navigation.

**Fix** — lyrics are optional content: the query must tolerate a 404 and render the page
without a lyrics panel, rather than failing the route. Worth auditing sibling routes for the
same pattern (any optional sub-resource whose 404 fails a parent route).

---

## I-21
### Nothing is visible without an account

**Severity: High** — growth and sharing are blocked, not just browsing.

Every route tested while signed out redirects to `/login`: `/store`, `/search`, `/podcasts`,
`/events`, `/artist/:id`, `/track/:id`, `/album/:id`. There is no public catalogue, no shareable
artist or track page, and no preview.

**Why it matters commercially** — when an artist shares a track link (WhatsApp, Instagram,
X), the recipient lands on a login form instead of the music. The 30-second preview that
INV-3 describes never gets a chance to do its job, because unauthenticated visitors never
reach a player.

**Decide explicitly**: if the gate is intentional, that is a product choice worth stating.
If it is not, artist/album/track/store pages should be readable while signed out, with buying
and library actions prompting sign-in.

---

## I-22
### No onboarding — the app never asks a new user anything

**Severity: Medium**

Signup goes straight from the form to a fully-populated home page. No genre selection, no
artist follow suggestions, no location or language preference. Every new fan gets an identical
generic home feed, and the platform collects nothing to personalise with — while a
"Made for you" rail claims the opposite (see [I-24](#i-24)).

---

## I-23
### The fallback image is missing, so broken images stay broken

**Severity: Medium**

**8 of 49 images** on the home page fail to load for a new account. Several point at
`/images/placeholder.jpg`, which does not exist — the request returns **200 with
`content-type: text/html`** (the SPA shell served for unknown paths), so the browser cannot
render it and the tile stays blank.

Affected in testing: playlist covers ("Made in Ghana"), artist avatars ("Camidoh",
"Kojo Producer", "Kofi Mensah"), album art ("Sugarcane").

**Fix** — ship an actual placeholder asset, and consider an `onError` handler so a failed
remote image degrades to it rather than to nothing.

---

## I-24
### The home page claims personalisation it cannot have, and pre-loads a song nobody chose

**Severity: Medium** — small, but it is the first thing a new user sees.

Three seconds after signup, the home page shows:

- A rail titled **"Made for you — Mixes and playlists picked for your taste."** No taste data
  exists; nothing was ever collected ([I-22](#i-22)).
- The player bar **already loaded with "Last Last" by Burna Boy**, plus a full up-next queue,
  none of which the user selected. It reads as though audio is playing or paused.

**Fix** — title the rail honestly for cold-start users ("Popular in Ghana", "Start here"), and
leave the player empty until the user picks something.

---

## I-25
### Completing an album costs more than buying the whole album

**Severity: Medium** — a pricing display that penalises existing customers.

On `/album/iron-boy`, with 2 of 6 tracks already marked owned, the page offers:

> **Buy rest • ₵7.50**   |   **Buy album • ₵6.00**

Buying the four remaining tracks costs **₵1.50 more** than buying all six. The bundle discount
applies to the full album but not to the remainder, so the more of an album a fan owns, the
worse their price to complete it.

**Fix** — apply the bundle discount to the remainder, or cap "Buy rest" at the album price.
Either way the two buttons should never be able to invert.

---

## I-26
### Search "Playlists" tab renders nothing, with no empty state

**Severity: Low**

Searching `black sherif` and selecting the **Playlists** tab returns zero results and displays
no message — just blank space below the tabs. The other tabs (All 9, Tracks 9, Artists 1,
Albums 2) all work correctly. It reads as a broken page rather than "no matches".

---

## I-27
### Becoming an artist crashes the app — the new token is never issued

**Severity: Critical** — dead-ends the entire artist acquisition funnel at step one.

**Steps**
1. Sign in as a fan. Go to `/studio`.
2. Click **Set up artist studio**.

**Expected** — the studio opens.

**Actual** — a raw developer error boundary:

> **Something went wrong!** *Hide Error* — Request failed

**The upgrade itself succeeds.** `POST /v1/me/become-artist` returns **200** with
`isArtist: true`. Every subsequent Studio call then fails:

```
GET /v1/studio/profile | /settings | /releases | /analytics | /audience | /payouts   → 403
```

**Root cause** — the browser still holds the token issued *before* the upgrade, whose claims
read `groups: ["fan"]`. `become-artist` returns the updated account object but **not a new
token**, and the frontend does not re-authenticate. Verified:

| Token | `groups` | Studio |
|---|---|---|
| Held after upgrade | `["fan"]` | 403 on everything |
| After logging out and back in | `["fan","artist"]` | 200 |

So a new artist must log out and back in to reach the studio they just created, with nothing
in the UI telling them so. The visible outcome is a crash screen.

**Fix** — have `become-artist` return a fresh token (or trigger a silent re-auth) and update
the stored session before navigating. Failing that, the studio route must handle 403 with a
"finishing setup, please sign in again" state rather than an unhandled throw.

---

## I-28
### Overview says "Nothing needs attention" while the review queue has items

**Severity: Medium** — an operator's dashboard tells them their shift is empty when it is not.

`/admin` displayed **"Nothing needs attention"** at the same moment `/admin/catalog` showed
**"Pending review · 2"**. The overview's attention panel is not counting the catalog queue.

Same page: the **"GMV by day (₵)"** card renders as a fully blank panel — no chart and no
empty-state message, unlike the neighbouring cards which correctly say "No artist revenue yet"
and "No payment-method data yet". It reads as a broken widget rather than an empty one.

---

## I-29
### The user list cannot show who is an admin

**Severity: Medium** — a trust-and-safety console that cannot identify privileged accounts.

`/admin/users` lists **QA Admin** — a `super-admin` — with role **Fan**.

```
GET /v1/admin/users → { "name":"QA Admin", "role":"fan", "status":"active" }
```

The role taxonomy on this endpoint is fan/artist only; admin membership is not represented, so
the frontend has nothing to render. Consequences for an operator:

- You cannot tell, from the user list, which accounts hold privileged access.
- You could suspend a colleague's admin account without realising what it was.
- You could not spot a compromised or unexpected admin account by browsing users.

**Fix** — surface admin membership and role on the admin user row (a badge beside fan/artist),
and consider a "Staff" filter chip alongside Fans / Artists / Verified / Suspended.
