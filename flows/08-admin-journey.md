# Flow 08 — A Shift on the Admin Console

Written from the seat of a platform operator starting their day: check what needs doing, work
the review queue, handle users, check the money, close out.

**Bottom line:** the admin console is the most trustworthy surface in the product — it reports
honest zeros instead of inventing figures — but it cannot tell me who I am, cannot tell me who
the other admins are, and its main review action always fails.

---

## Act 1 — Signing in and orienting

`/admin` loads a clean console: Overview, Users, Catalog, Moderation, Finance, Editorial,
Health, Trust & safety, Support, Compliance, Audit log, Settings.

### The console does not know who I am

The sidebar footer says:

> **AD**  ·  **Admin · Yaa**  ·  🛡 **SUPER-ADMIN**

I am signed in as **QA Admin**. "Yaa" is a hardcoded constant, shown to every admin who ever
logs in. Worse, the **SUPER-ADMIN** badge is hardcoded too — it is not read from my session, so
a `support` agent or `moderator` sees the same green shield.

On a console whose whole job is controlling privilege, the privilege indicator is decorative.
→ [I-3](ISSUES.md#i-3)

### The overview itself is honest, and should be the template

| | |
|---|---|
| Active users | **10** total |
| Streams | **0** — |
| GMV | **₵0** — |
| New artists | **3** last 7 days |

Plus *"Nothing needs attention"*, *"No artist revenue yet"*, *"No payment-method data yet"*.

This is exactly right: real counts where they exist, an em dash where nothing is measured, and
plain empty states elsewhere. **Every fabricated screen elsewhere in the app should be
rewritten to look like this page.**

Two blemishes:

- The **"GMV by day (₵)"** card renders as a completely blank panel — no chart, no
  "no data yet" message, unlike its neighbours. It reads as broken.
- **"Nothing needs attention"** was wrong: the Catalog had **2 items in its review queue** at
  that moment. The dashboard told me my shift was empty when it was not.
→ [I-28](ISSUES.md#i-28)

---

## Act 2 — Working the review queue

`/admin/catalog` showed **"Pending review · 2"** and I clicked **Approve** on the first item.

> **Could not approve release**

```
POST /v1/admin/catalog/019f8c16-…/approve
→ 409 {"code":"ILLEGAL_TRANSITION",
       "message":"Cannot apply 'APPROVE_IMMEDIATE' to a release in status 'draft'"}
```

The queue is listing **drafts**, not submissions:

```
GET /v1/admin/catalog?status=pending
→ [{ "title":"Kumasi Nights", "status":"draft" },
   { "title":"I love yoj",    "status":"draft" }]
```

One of those is the half-finished single I abandoned in the artist journey — I never submitted
it. **An artist's private, unfinished draft is sitting in the moderator's review queue**, with
an Approve button that can never work and an error message that says "try again".

Two problems to settle: drafts should not count as "pending review", and a row should not offer
an action that is invalid for its status.
→ [I-19](ISSUES.md#i-19)

Above the queue, the page states **"1,260 artists · 18,420 albums · 142,800 tracks"** as fact.
The real catalogue is a few dozen records. → [I-9](ISSUES.md#i-9)

---

## Act 3 — Users

**This is the best-built screen in the console.** Search by name/email/ID, filter chips
(All · 10, Fans · 4, Artists · 6, Verified · 2, Suspended · 0), multi-select checkboxes,
columns for user, email, role, joined, last active and status, a per-row overflow menu, and
working pagination.

### Suspending a user works properly

Row menu → **Suspend**:

```
POST /v1/admin/users/019fbaad-…/suspend  →  list refetched
```

The toast said **"Adjoa Bediako suspended"**, her status badge flipped to `suspended`, and the
filter chip updated to **Suspended · 1** in the same beat. Reversible via `/reactivate`
(which I used to restore her). This is what every action in the app should feel like.

### But the Role column cannot show admins

"QA Admin" — a **super-admin** — is listed with role **Fan**.

```
GET /v1/admin/users → { "name":"QA Admin", "role":"fan", "status":"active" }
```

The backend's role taxonomy is fan/artist only; admin membership is not represented. So from
the user list, **an operator cannot tell which accounts hold privileged access**. You could
suspend a colleague's admin account without realising what it was, and you could not spot a
compromised admin account by browsing.
→ [I-29](ISSUES.md#i-29)

### The user detail page is fiction

Opening a user shows activity, orders, devices and stats that are mock data, identical for
every user, ignoring the id: Releases 12, Revenue ₵42K, Followers 412K, Lifetime spend ₵312,
Playlists 7. A support agent investigating a complaint is reading invented history attributed
to a real person. → [I-8](ISSUES.md#i-8)

---

## Act 4 — The money

### Finance overview

| | |
|---|---|
| GMV (MTD) | ₵0 · 0% |
| Platform fee | ₵0 · 30% take |
| Artist payouts due | **₵9k · 1 artists** |
| MoMo float | — not yet available |

The pending payouts table is where it gets uncomfortable:

```
ARTIST                                 AMOUNT     METHOD          STATUS       ACTION
019f872e-7299-7e99-a08b-8b38f0e51879   ₵9,400.5   MoMo · Telecel  KYC pending  Send
```

**The artist column is a raw account UUID.** The **Send** button next to it moves real money,
and the operator cannot tell who they are paying. The amount is also unformatted —
**₵9,400.5**, where every other figure in the console uses two decimals.
→ [I-16](ISSUES.md#i-16)

I did **not** press Send or "Run weekly payout" — those move real money and that is the team's
call, not mine.

### Ledger

`/admin/finance/ledger` is properly wired: real entries with date, type, party, ref and amount,
filter chips per type, and **"Net in view · ₵109,367.50"**.

Worth a sanity check by someone who knows the intended semantics: the Finance overview reports
**GMV ₵0** while the ledger shows six figures of movement in the same month. That may be
correct (the ledger rows are payouts, not sales, and GMV counts sales) — but two money screens
one click apart showing ₵0 and ₵109,367.50 will be questioned by whoever reads them, and the
labels should make the distinction obvious.

---

## Act 5 — The rest of the shift

| Screen | Verdict |
|---|---|
| **Moderation** | Real queue, honest zeros ("0 open · 6h SLA · 0 escalated"), filters by type |
| **Trust & safety** | **Honest** — "— not measured yet" on the three unbacked KPIs |
| **Support** | Real, empty after fixture cleanup |
| **Compliance** | Real, empty after fixture cleanup |
| **Audit log** | **Genuinely good** — real entries with actor, action, target and time |
| **Settings** | **Works** — changed the platform fee, `PUT` + refetch + "Platform settings saved" (restored to 30 afterwards). Provider toggles correctly disabled with a note |
| **Health** | **Fails** — see below |

### Health says everything is fine without checking

> **All systems normal**
> No service metrics yet.
> Concurrent listeners (24h) — No listener telemetry yet.
> Recent incidents — No incidents recorded.

The headline is not derived from anything. If a service were down, this page would still say
"All systems normal". The three panels below it are honest; the banner above them is not, and
the banner is what an operator glances at. → [I-17](ISSUES.md#i-17)

### Decorative controls

**Export CSV** on Audit log, Ledger and Users all toast *"Exporting…"* as a **success** and
produce no file — there is no export endpoint. Editorial's **New playlist**, **Replace slot**,
**Schedule push** and **Open playlist** are toast-only. On the user detail page, **Sign out of
device** does not sign anything out.

---

## How it felt

Working a shift here is mostly reassuring. When the console has data it shows it; when it has
none it says so plainly — "no artist revenue yet", "not measured yet", "nothing needs
attention". After the fan and artist dashboards, that restraint is a relief, and it is the
standard the rest of the product should be held to.

What undermines it is smaller but sharper: I cannot see my own name or my real role, I cannot
tell which of my colleagues are admins, my main review action always fails on items that should
not be in the queue, and the one screen I would check in an incident — Health — is the one
screen that tells me everything is fine without looking.

## If the team fixes four things here, fix these

1. **Real admin identity and role** in the shell — same fix already applied to Studio in
   PR #178 — [I-3](ISSUES.md#i-3).
2. **Stop listing drafts as pending review**, and hide actions invalid for a row's status —
   [I-19](ISSUES.md#i-19).
3. **Show the artist's name, not a UUID, next to a Send-money button** —
   [I-16](ISSUES.md#i-16).
4. **Make the Health banner reflect measured state** — or say "no health data", like the panels
   under it already do — [I-17](ISSUES.md#i-17).
