# Flow 03 — Artist Studio

Run as `ama.artist.qa@beatzclik.local`, a verified artist with **zero releases** and
**zero earnings**. Every non-zero figure the Studio shows is therefore invented.

Related issues: [I-1](ISSUES.md#i-1).

---

## 3.1 Studio access and identity

| | |
|---|---|
| **Steps** | Sign in as the artist → `/studio` |
| **Expected** | The signed-in creator's own name and verification status |
| **Actual** | Sidebar **"AS · Ama Serwaa · VERIFIED"**; header **"Good evening, Ama"** and "Here's how Ama Serwaa is doing this month" |
| **Result** | **Pass** |

This confirms PR #178 in the running app. Before that fix every creator was shown as
**Black Sherif** — wrong name, wrong initials, an unconditional Verified badge, and
"Preview page" navigating to `/artist/black-sherif` instead of their own page.

---

## 3.2 Overview KPIs — **FAIL (critical)**

| | |
|---|---|
| **Expected** | Zeros. This artist has no releases and no earnings |
| **Actual** | **This month ₵21,680 (+24%)** · **Streams 412K (+18%)** · **Monthly listeners 2.4M (+18%)** · Available balance ₵0.00 |
| **Result** | **Fail** — see [I-1](ISSUES.md#i-1) |

Ground truth from the API:

```
GET /v1/studio/releases  → {"items":[],"total":0}
GET /v1/studio/payouts   → {"available":0.00,"thisMonth":0.00,"lifetime":0.00,"transactions":[]}
GET /v1/studio/analytics → {"fans":0,"engagement":{"completion":0,"save":0,"skip":0},"countries":[],"ages":[]}
```

Three of the four cards are mock (`studio.index.tsx:35-37`); the fourth, "Available
balance", is real. They are rendered in the same row with no visual distinction.

**The damaging part is the juxtaposition.** "₵21,680 earned this month" next to
"₵0.00 available to withdraw" does not read as a bug — it reads as *money owed but not yet
released*. An artist would be right to think they are due ₵21,680 and to ask where it is.

---

## 3.3 Overview → Payouts contradiction — **FAIL**

| | |
|---|---|
| **Steps** | On `/studio`, click the **This month** KPI card (it links to Payouts) |
| **Actual** | `/studio/payouts` shows **"THIS MONTH ₵0 · 0%"**, **LIFETIME ₵0**, "This month by source: Track sales ₵0, Streaming ₵0", available ₵0.00 |
| **Result** | **Fail** |

Same metric, same artist, one click apart: **₵21,680 → ₵0**. Payouts is the page telling the
truth; it is correctly wired to `payoutsQuery()`.

---

## 3.4 Detail pages are correctly wired

Checked because it narrows the fix: `/studio/analytics`, `/studio/audience` and
`/studio/payouts` all use the real queries (`studioAnalyticsQuery`, `studioAudienceQuery`,
`payoutsQuery`) and contain **no** mock getters.

**Only the overview was left behind.** The queries it needs already exist and are proven in
use by its own sub-pages, so this is a swap, not new integration work.

---

## 3.5 Profile

| | |
|---|---|
| **Actual** | Loads the creator's real profile; "Preview page" now navigates to the creator's own artist page |
| **Result** | **Pass** (PR #178) |

The featured-track picker was also moved off mock data onto `artistTracksQuery` in that PR.
For an artist with no published tracks the picker is empty, which is correct.

---

## 3.6 Release creation — blocked, not tested

The release wizard could not be exercised end to end. Two pre-existing backend defects block
it (recorded previously, outside this QA pass):

1. S3 upload fails with a 500 on mark/reset during track upload.
2. `artist_profile` is not provisioned when an account is upgraded to artist.

Until these are fixed, an artist cannot publish anything through the UI, which also means
the purchase and ownership flows cannot be tested against real creator content.

---

## Summary

Studio identity is now correct. The remaining problem is concentrated in **one file**:
the overview dashboard shows fabricated revenue, streams and listener figures that its own
sub-pages immediately contradict. Given that this is the first screen an artist sees after
signing in, and that it concerns their money, it should be the first thing fixed.
