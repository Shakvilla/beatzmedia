# BeatzClik — QA Flow Documentation

Manual, end-user QA of the running app. Every finding here was reproduced through the
browser UI at `http://localhost:5173`, not by reading code or calling the API. Where a
root cause is named, it was traced back to source afterwards and the file:line is given.

**Test date:** 2026-07-31
**Build:** `master` @ `fff661f` + branch `fix/studio-creator-identity` (PR #178)
**Backend:** Quarkus dev mode on `:18080`, host Postgres `beatzmedia`

## Documents

| File | Covers |
|---|---|
| [ISSUES.md](ISSUES.md) | **Start here.** Every defect, prioritised, with repro steps and root cause |
| [01-auth.md](01-auth.md) | Signup, login, logout, session expiry, route protection |
| [02-fan-purchase.md](02-fan-purchase.md) | Browse → cart → checkout → payment → ownership |
| [03-artist-studio.md](03-artist-studio.md) | Studio overview, payouts, analytics, profile |
| [04-admin.md](04-admin.md) | Admin console identity and data |
| [05-feature-matrix.md](05-feature-matrix.md) | **Every sidebar item and action, per role, with status** |
| [06-fan-journey.md](06-fan-journey.md) | **The first-time fan experience, start to finish** |
| [07-artist-journey.md](07-artist-journey.md) | **The first-time artist experience, start to finish** |
| [08-admin-journey.md](08-admin-journey.md) | **A shift on the admin console** |

## Environment

Two backends exist locally and **they do not share a database**. Everything below assumes
dev mode, which is where the QA accounts live.

```bash
cd backend && ./mvnw quarkus:dev -Dquarkus.http.port=18080
```

```bash
cd Frontend && nvm use 22.17.1 && npm run dev
```

`Frontend/vite.config.ts` must proxy `/v1` to `127.0.0.1:18080`. Host `:8080` is a
different, unrelated application that rejects every request with
`TENANT_MISSING: Missing X-Tenant-ID header`.

## Test accounts

| Role | Email |
|---|---|
| Fan | `kofi.fan.qa@beatzclik.local` |
| Artist | `ama.artist.qa@beatzclik.local` |
| Admin (super-admin) | `qa.admin@beatzclik.local` |

Passwords are **not** recorded here — these accounts exist only in a local dev database and
their credentials are shared out of band. Recreate them with `POST /v1/auth/signup`, then
promote the admin by setting `account.is_admin = true` and inserting an `admin_member` row
(`LoginService` gates the admin role on `is_admin`, so the flag alone is not enough).

All three are freshly created and own nothing. **That is deliberate** — an empty account is
what exposes fabricated data, because any non-zero figure it displays must be invented.

## Headline

All 35 routes render and none throw. The problems are not broken pages — they are
**features that look finished and are not**, in two forms.

**1. Invented data shown beside real data.** An artist with zero releases sees
**"This month ₵21,680"** on the Studio overview, clicks that card, and lands on Payouts
showing **"This month ₵0"** ([I-1](ISSUES.md#i-1)).

**2. Controls that report success without doing anything.** 26 of them. Tipping an artist
shows "₵5 tipped via MoMo 💚" and makes **zero network calls** — while a complete tipping
backend sits unused ([I-14](ISSUES.md#i-14)). "Unpublish" says a release is down while it
is still live. "Deactivate" says a profile is off while it is on.

And the one that matters most for a music product: **no audio ever plays.** The player bar
animates a progress counter with no `<audio>` element anywhere ([I-12](ISSUES.md#i-12)).

Start with [05-feature-matrix.md](05-feature-matrix.md) for the per-menu status table.
