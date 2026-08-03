# Flow 07 — The First-Time Artist

Written from the seat of a musician. I took the fan account I had just created
(Adjoa Bediako) and did what a Ghanaian artist would do after hearing they can sell music
here: upgraded, set up a profile, and tried to release a single.

**Bottom line:** the moment I clicked "Set up artist studio", the app crashed. Once I worked
around that, the dashboard congratulated me on **₵21,680 earned and 412K streams** for music
I have never made — on the same screen that told me to upload my first release.

---

## Act 1 — Becoming an artist

### The upsell is good

`/studio` as a fan shows a genuinely well-made pitch: *"Start selling your music — turn your
account into an artist studio to upload releases, track your audience and get paid, free to
set up."* Three clear benefits (release & sell, know your audience, get paid to MoMo) and one
button: **Set up artist studio**.

No complaints. This is how it should read.

### Clicking that button crashes the app

I clicked it and got a raw error screen:

> **Something went wrong!**  *Hide Error*
> Request failed

Not a friendly message — the developer error boundary.

**The upgrade actually succeeded.** `POST /v1/me/become-artist` returned **200** with
`isArtist: true`. The crash is in what happens next: every Studio call fails.

```
GET /v1/studio/profile    → 403
GET /v1/studio/settings   → 403
GET /v1/studio/releases   → 403
GET /v1/studio/analytics  → 403
GET /v1/studio/audience   → 403
GET /v1/studio/payouts    → 403
```

**Root cause** — the JWT is stale. The token in the browser was issued before the upgrade, so
it still says `groups: ["fan"]`. `become-artist` returns the updated *account* but **not a new
token**, and the frontend never refreshes it.

Proven by logging out and back in:

| | groups |
|---|---|
| Token held after upgrading | `["fan"]` → Studio 403s |
| Token after re-login | `["fan","artist"]` → Studio 200 |

**So a new artist must log out and log back in to enter the studio they just created — and
nothing tells them that.** All they see is "Something went wrong!". Most people would assume
the product is broken and leave.

→ [I-27](ISSUES.md#i-27)

---

## Act 2 — The dashboard invents my career

Once I had a valid token, `/studio` loaded. I had been an artist for about ninety seconds and
had uploaded nothing. Here is what it told me:

| | |
|---|---|
| **This month** | **₵21,680** ▲24% |
| **Streams** | **412K** ▲18% |
| **Monthly listeners** | **2.4M** ▲18% |
| Available balance | ₵0.00 |

Below that, a **rising streams chart**, an **Audience** panel (412K listeners, 8,420 followers,
top city Accra), and a **Top tracks** table:

| # | Track | Plays | Earned |
|---|---|---|---|
| 1 | Kwaku the Traveller | 142K | ₵6,420 |
| 2 | Soja | 98K | ₵3,890 |
| 3 | 45 | 64K | ₵2,140 |

**I did not make any of those songs.** They belong to other artists on the platform, and the
dashboard is presenting them as mine, with earnings attached.

The server disagrees with all of it — `GET /v1/studio/releases` returns `total: 0`.

And the giveaway is on the very same screen: the **Needs attention** panel says

> **Drop your first release** — Upload a single, EP or album

So the page simultaneously knows I have released nothing and credits me with 412K streams and
₵21,680. Only "Available balance ₵0.00" is real, which makes it worse — an artist reading
"₵21,680 earned" beside "₵0.00 available" will conclude the platform owes them money and is
withholding it.

→ [I-1](ISSUES.md#i-1) (this journey extends it well beyond the KPI strip: top tracks,
audience panel, streams chart and recent activity are all fabricated too)

---

## Act 3 — Setting up my profile

This worked. I filled in display name (`Adjoa B`), username (`adjoab`) and hometown
(`Kumasi, Ghana`), hit **Save changes**, and got *"Profile changes saved"*. The server
confirmed all three fields.

**One trap:** if you leave **Username** blank — which is its default state for a brand-new
artist — the save fails with *"Could not save your profile. Please try again."* The real
reason is `422 Username is required`, which the API says explicitly and the UI throws away.
The field isn't marked required, so there's no way to guess. Trying again fails identically,
forever. → [I-18](ISSUES.md#i-18)

Small thing: my sidebar avatar read **AS** while the profile page's own avatar read **?**, until
I set a display name. → [I-15](ISSUES.md#i-15)

---

## Act 4 — Releasing a single

This is the part I most wanted to work, and it is in far better shape than I expected.

The wizard is four clear steps — **Details → Tracks → Splits → Review** — with a persistent
progress rail, **Save draft** on every step, and back navigation.

| Step | What happened |
|---|---|
| **1. Details** | Title, primary artist, featured artists, label, release date, genre, description, cover. **The primary-artist placeholder correctly showed "Adjoa B"** (before PR #178 this said "Black Sherif" for everyone). **Continue** created a real draft — `POST /v1/studio/releases`. |
| **2. Tracks** | Audio upload worked — `POST /v1/studio/releases/:id/tracks`. My synthetic test file came back as *"metadata missing"* with a **Replace file** option, which is reasonable handling. Price chips ₵2.00 / ₵2.50 / ₵3.00 / Free. |
| **3. Splits** | Correct: my own row pre-filled as **"Adjoa B", Performer · Writer, 100%**, total 100%, with **Add collaborator** and a **Solo (100% me)** preset. Also correct before #178? No — this row used to say "Black Sherif". |
| **4. Review** | A real summary — *Kumasi Nights, Adjoa B · Single · 1 track, PRICE ₵2.50* — plus a checklist with **Fix** links for cover art, track metadata and splits. |

### The validation is genuinely good

Submitting without a cover gave **"Add cover art before submitting"**. After adding one,
it gave **"Accept the distribution agreement to submit"**. Both are specific, actionable, and
blocked the action rather than faking success — the opposite of the toast-only controls
elsewhere in the app.

**I stopped there.** Accepting a distribution agreement is a legal action and I would not
click it on your behalf, even on a QA account. Everything up to that gate works, so the
previously-recorded "release creation is blocked" note is now out of date — the S3 upload
error did not reproduce.

### One thing to check on this screen

At the price step the UI promises **"You earn 70% per sale · ₵1.75 per track"**. That 70% is
hardcoded in the frontend (`CREATOR_REVENUE_SHARE = 0.7`) while an admin can now change the
platform fee in Settings. Change the fee to 35% and this screen still promises artists 70%,
at the exact moment they are deciding what to charge.

Also: on the Splits step the panel read **"FREE TRACK · BREAKDOWN — this track is free"**
even though I had selected ₵2.50, while the Review step correctly showed ₵2.50. Worth a look —
possibly stale state between steps.

---

## Act 5 — The rest of the studio

| Page | Verdict |
|---|---|
| **Releases** | Correct — "No releases yet · Drop your first single, EP or album" |
| **Podcasts** | Correct empty state, "New episode" CTA |
| **Analytics** | **Honest**: streams 0, track sales ₵0, new followers 0 |
| **Audience** | **Honest**: 0 monthly listeners, 0 followers, 0 superfans |
| **Payouts** | **Honest**: ₵0.00 available, ₵0 this month, ₵0 lifetime |
| **Settings** | Loads real data; many rows are decorative (below) |

The contradiction is stark when you walk it: **Overview says 412K streams and ₵21,680.
Analytics, Audience and Payouts — the three pages Overview links to — all say zero.**

### Settings rows that do nothing

Each toasts success and makes no request: **Change password** ("Password reset link sent to
your email"), **Rights & ownership verification**, **Manage billing**, **View invoices**, and —
most alarming — **Deactivate profile** ("Profile deactivated — reactivate any time"), which
does not deactivate anything.

On Releases: **Duplicate** ("Duplicated … as a draft" — no draft appears) and **Unpublish**
("… unpublished" — the release stays live). On Audience: **Thank superfans**. On Payouts:
**Export transactions CSV**.
→ [I-14](ISSUES.md#i-14) family, catalogued in [05-feature-matrix.md](05-feature-matrix.md)

---

## How it felt

The release wizard is the best-built thing in this application — clear steps, real
persistence, honest blocking validation, and specific error messages. Someone took care over
it.

That makes the dashboard harder to forgive. An artist's first impression is a screen telling
them they earned ₵21,680 from songs they never wrote, next to a ₵0.00 balance. The first
person who reads that carefully will not think "nice demo data" — they will think the platform
is holding their money, or that their catalogue has been mixed up with someone else's. For a
product asking Ghanaian musicians to trust it with their income, that is the most expensive
possible first impression.

## If the team fixes three things here, fix these

1. **Issue a fresh token on `become-artist`** (or force a re-auth) so signing up as an artist
   doesn't dead-end on a crash screen — [I-27](ISSUES.md#i-27).
2. **Wire the Studio overview to the queries its own sub-pages already use** — the fix is
   swapping three mock getters in one file — [I-1](ISSUES.md#i-1).
3. **Surface `error.field` on profile save** so a new artist knows Username is required —
   [I-18](ISSUES.md#i-18).
