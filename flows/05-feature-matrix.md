# Feature Matrix — every sidebar item, every surface

Walked menu by menu as each role. **Renders** = the page loads without an error boundary.
**Wired** = the feature actually reaches the backend and does what it claims.

Legend: ✅ works · ⚠️ renders but shows invented data · ❌ claims success without doing anything ·
🚫 not implemented · ⛔ blocked, untestable

---

## Fan — main navigation

| Menu item | Renders | Wired | Notes |
|---|---|---|---|
| Home | ✅ | ✅ | Real feed: featured albums, trending, playlists |
| Search | ✅ | ⚠️ | Real results. Mislabels a not-for-sale track as **OWNED** — [I-13](ISSUES.md#i-13) |
| Library | ✅ | ✅ | Honest empties ("0 songs", "0 tracks") |
| Store (Overview) | ✅ | ✅ | 17 products |
| Store → Hi-Fi / Beats / Merch / Exclusives | ✅ | ✅ | Tabs filter correctly (5 / 4 / 4 / 3 products) |
| Podcasts | ✅ | ✅ | Featured show, categories |
| Events | ✅ | ✅ | Event cards with dates, venues, prices |
| Your Playlists → Liked Songs | ✅ | ✅ | |
| Notifications | ✅ | ✅ | Honest "No notifications yet." |
| Cart | ✅ | ✅ | Persists server-side; correct totals |
| Settings | ✅ | ⚠️ | Hardcoded "₵312 SPENT" — [I-6](ISSUES.md#i-6) |
| Lyrics | ✅ | ⚠️ | Renders from `lyrics-data.ts` — confirm whether a lyrics endpoint is planned |
| Artist Studio (link) | ✅ | ✅ | Correctly gated by role |

## Fan — actions

| Action | Status | Evidence |
|---|---|---|
| **Play a track** | 🚫 | **No audio ever plays** — [I-12](ISSUES.md#i-12) |
| Add to cart | ✅ | `POST /v1/me/cart/items → 200`, survives reload |
| Checkout / pay | ⚠️ | Order created (`202`), then hangs forever — [I-2](ISSUES.md#i-2) |
| Follow artist | ✅ | `POST /v1/me/follows/artists/:id` + collection refetch |
| Create playlist | ✅ | `POST /v1/me/playlists`, navigates to the new playlist |
| **Tip an artist** | ❌ | **Zero network calls; claims money sent** — [I-14](ISSUES.md#i-14) |
| **Tip a podcast** | ❌ | Same handler, same problem |
| Share track / playlist | ❌ | "Link copied to clipboard" — nothing is copied |
| Upgrade to Premium | ❌ | Toast only |
| Edit profile | ❌ | Toast only |
| Clear cache | ❌ | "Cache cleared" — nothing cleared |
| Social sign-in (FB/Google/X) | ⚠️ | Honest: "coming soon — use email for now" |

---

## Artist Studio — sidebar

| Menu item | Renders | Wired | Notes |
|---|---|---|---|
| Overview | ✅ | ❌ | **Mock KPIs contradicting 3 real pages** — [I-1](ISSUES.md#i-1) |
| Releases | ✅ | ✅ | Correct empty state |
| Podcasts | ✅ | ✅ | Correct empty state |
| Analytics | ✅ | ✅ | Real zeros: streams 0, sales ₵0, followers 0 |
| Audience | ✅ | ✅ | Real zeros: 0 listeners, 0 followers |
| Payouts | ✅ | ✅ | Real ₵0.00 |
| Profile | ✅ | ⚠️ | Works; avatar shows `?` when display name is blank — [I-15](ISSUES.md#i-15) |
| Settings | ✅ | ⚠️ | Loads real settings; several rows are toast-only (below) |

## Artist Studio — actions

| Action | Status | Notes |
|---|---|---|
| Create release | ⛔ | Blocked by two backend defects (S3 mark/reset 500; `artist_profile` not provisioned) |
| Duplicate release | ❌ | "Duplicated … as a draft" — no draft created |
| Unpublish release | ❌ | "… unpublished" — **still live** |
| Export transactions CSV | ❌ | "Exporting transactions as CSV" — no file |
| Thank superfans | ❌ | "Thank-you sent to your top superfans 💚" — nothing sent |
| Resend split invite | ❌ | "Invite re-sent" — no invite |
| Change password | ❌ | "Password reset link sent to your email" — no email |
| Start rights verification | ❌ | Toast only |
| Manage billing / invoices | ❌ | Toast only |
| **Deactivate profile** | ❌ | "Profile deactivated — reactivate any time" — **not deactivated** |
| Podcast episode play / edit | ❌ | Toast only |

---

## Admin — sidebar

| Menu item | Renders | Wired | Notes |
|---|---|---|---|
| Overview | ✅ | ✅ | **Best-behaved page in the app** — real counts, `—` where unmeasured |
| Users | ✅ | ✅ | Real list (9 users, correct role/status counts) |
| Catalog | ✅ | ⚠️ | Hardcoded totals over a real list — [I-9](ISSUES.md#i-9) |
| Moderation | ✅ | ✅ | Real queue, honest zeros |
| Finance | ✅ | ⚠️ | **Payout row shows a raw UUID as the artist** — [I-16](ISSUES.md#i-16) |
| Finance → Ledger | ✅ | ✅ | Real entries |
| Editorial | ✅ | ⚠️ | Data real; New playlist / Replace / Push / Open are toast-only |
| Health | ✅ | ❌ | **"All systems normal" while admitting no metrics** — [I-17](ISSUES.md#i-17) |
| Trust & safety | ✅ | ✅ | Honest "— not measured yet" |
| Support | ✅ | ✅ | Real (empty after fixture cleanup) |
| Compliance | ✅ | ✅ | Real (empty after fixture cleanup) |
| Audit log | ✅ | ✅ | Real entries with actor and time |
| Settings | ✅ | ✅ | Real save; provider toggles correctly disabled |

## Admin — actions

| Action | Status | Notes |
|---|---|---|
| Export CSV (audit / ledger / users) | ❌ | All three toast "Exporting…" as **success**; no file, no endpoint |
| Editorial: new playlist / replace slot / push | ❌ | Toast only |
| User detail: sign out device | ❌ | "Signed out of device" — not signed out |
| Catalog item: preview track | ❌ | Toast only |
| Admin team & roles | ⚠️ | Labelled local-only, but `AdminTeamResource` exists — re-check |

---

## Cross-cutting

| Item | Status |
|---|---|
| Admin identity ("Admin · Yaa", SUPER-ADMIN badge) | ❌ [I-3](ISSUES.md#i-3) |
| Studio identity (Ama Serwaa) | ✅ fixed in PR #178 |
| Session expiry (15 min, no refresh) | ❌ [I-5](ISSUES.md#i-5) |
| Route protection / role gating | ✅ |
| Tab title | ❌ still `temp-app` |

---

## The pattern worth naming

Counting the ❌ rows: **26 controls report success for work that never happened.** They are not
failures the user can see — each one shows a green confirmation. Unpublish says the release is
down while it is still live; Deactivate says the profile is off while it is on; Tip says money
was sent when none moved.

A user cannot distinguish these from working features. That is the single most important
thing for the team to address, and the cheapest interim fix is honest copy: switch the
`'success'` toasts to `'info'` with "not available yet", exactly as was done for the three
admin stubs in PR #177.

---

## Round 2 — actions verified individually (network-instrumented)

Each was clicked in the UI with `window.fetch` wrapped, so "wired" means a request was
actually observed, not inferred.

| Action | Role | Result | Evidence |
|---|---|---|---|
| Like a track | Fan | ✅ | `POST /v1/me/likes/tracks/last-last` + collection refetch |
| Add to playlist | Fan | ✅ | picker → `POST /v1/me/playlists/{id}/tracks/{trackId}` |
| Create playlist | Fan | ✅ | `POST /v1/me/playlists`, navigates to it |
| Follow artist | Fan | ✅ | `POST /v1/me/follows/artists/{id}` |
| Cart — increase quantity | Fan | ✅ | `PATCH /v1/me/cart/items/store:…` |
| Cart — remove item | Fan | ✅ | `DELETE /v1/me/cart/items/store:…` |
| Event ticket "Buy with MoMo" | Fan | ✅ | `POST /v1/me/cart/items`, routes to cart |
| **Tip artist / podcast** | Fan | ❌ | **zero requests** — [I-14](ISSUES.md#i-14) |
| **Play** | Fan | 🚫 | no `<audio>` element — [I-12](ISSUES.md#i-12) |
| **Save studio profile** | Artist | ❌ | `PUT` → 422 "Username is required"; generic error — [I-18](ISSUES.md#i-18) |
| Save platform settings | Admin | ✅ | `PUT` + refetch + "Platform settings saved" (fee restored to 30 after test) |
| **Approve catalog item** | Admin | ❌ | `POST …/approve` → 409 ILLEGAL_TRANSITION — [I-19](ISSUES.md#i-19) |

## Detail pages opened directly

| Page | Renders | Note |
|---|---|---|
| `/album/iron-boy` | ✅ | Falsely claims **"YOU OWN 2/6"** |
| `/track/last-last` | ✅ | Falsely claims **"In your collection"** |
| `/artist/rema` | ✅ | Follow / Tip / Share present |
| `/playlist/made-in-ghana` (curated) | ✅ | |
| `/playlist/{own-id}` | ✅ | Correct empty state + Delete |
| `/event/five-star-night` | ✅ | Ticket tiers priced |
| `/podcast/sincerely-accra` | ✅ | |
| `/store/hifi-last-last` | ✅ | |

## Still not covered

Being explicit so nobody assumes these passed:

- **Admin:** moderation resolve/escalate, user suspend/ban, dispute refund/reject/escalate,
  run-weekly-payout and per-artist Send (deliberately skipped — they move real money),
  support ticket reply/assign, editorial drag-reorder, `admin/users/:id`,
  `admin/catalog/:id`, `admin/finance/dispute/:id`
- **Artist:** release wizard steps (blocked upstream), podcast episode create, withdraw,
  studio settings save round-trip
- **Fan:** podcast premium/preview gating, unfollow, unlike, playlist delete, checkout with
  card/other providers, order history
- **Non-functional:** responsive/mobile layouts, dark/light toggle, keyboard and screen-reader
  accessibility, offline and slow-network behaviour, 404 handling
