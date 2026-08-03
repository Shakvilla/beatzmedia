# Flow 04 — Admin console

Run as `qa.admin@beatzclik.local` (account name **QA Admin**, role `super-admin`).

Related issues: [I-3](ISSUES.md#i-3), [I-8](ISSUES.md#i-8), [I-9](ISSUES.md#i-9).

---

## 4.1 Access

| | |
|---|---|
| **Steps** | Sign in as the admin → `/admin` |
| **Actual** | Console loads with the full navigation: Overview, Users, Catalog, Moderation, Finance, Editorial, Health, Trust & safety, Support, Compliance, Audit log, Settings |
| **Result** | **Pass** |

Non-admins are correctly refused: the fan token gets `403` on `/v1/admin/settings`.

---

## 4.2 Admin identity — **FAIL**

| | |
|---|---|
| **Expected** | The signed-in admin's name and their actual role |
| **Actual** | Sidebar footer reads **"AD · Admin · Yaa"** with a green **SUPER-ADMIN** shield. The account is named "QA Admin" |
| **Result** | **Fail** — see [I-3](ISSUES.md#i-3) |

Both the name and the role badge are hardcoded (`lib/admin-data.ts:15-19`, rendered at
`components/admin/admin-shell.tsx:66-71`). The role is not read from the session at all, so
a `support` or `moderator` member also sees **SUPER-ADMIN**.

This is the same defect as `studioArtist`, which PR #178 fixed for Studio. The same
approach applies: derive name and initials from the session, and the role from the admin
member record.

---

## 4.3 Overview data — **PASS, and worth keeping**

| | |
|---|---|
| **Actual** | ACTIVE USERS **9** (total) · STREAMS **0** (—) · GMV **₵0** (—) · NEW ARTISTS **2** (last 7 days) · "Nothing needs attention." |
| **Result** | **Pass** |

This is the correct pattern and a useful contrast with the Studio overview. On an empty
platform it shows real counts where they exist and **`—` where nothing is measured**,
instead of inventing plausible numbers. The delta placeholders read "—" rather than a
fabricated "+18%".

---

## 4.4 User detail — **FAIL**

`/admin/users/:id` renders activity, orders and devices from `getUserDetail()` — a mock that
ignores the id — alongside hardcoded stat values (`routes/admin.users.$userId.tsx:28,69-70`):
Releases 12, Revenue ₵42K, Followers 412K, Lifetime spend ₵312, Playlists 7.

A support agent investigating a complaint is reading invented history attributed to a real
user. See [I-8](ISSUES.md#i-8).

---

## 4.5 Catalog totals — **FAIL**

`/admin/catalog` states **"1,260 artists · 18,420 albums · 142,800 tracks"** as fact, from
`CATALOG_SUMMARY` in `lib/admin-data.ts:488`. The dev catalogue holds a handful of records.
See [I-9](ISSUES.md#i-9).

---

## 4.6 Not re-tested this pass

The remaining admin screens — Finance, Moderation, Editorial, Trust & safety, Support,
Compliance, Audit log, Settings — were wired and QA'd in earlier work and are not re-covered
here. Two caveats the team should carry forward:

- **The QA fixtures used to exercise those screens have been removed**, so several will now
  show empty states. Empty is correct on a clean database; it is not evidence of breakage.
- **"Admin team & roles"** in Settings is labelled local-only on the grounds that no endpoint
  exists. That may no longer hold: `identity/adapter/in/rest/` contains `AdminTeamResource`,
  `InviteRequest` and `RoleChangeRequest`. The original check searched only
  `admin/adapter/in/rest`. Worth confirming before trusting the "not saved yet" note.

---

## Summary

The admin console's **data** is in the best shape of the three surfaces — it reports honest
zeros and em dashes rather than fabricating figures. Its **identity** is in the worst shape:
every admin is shown as "Yaa" with a SUPER-ADMIN badge nobody verified.
