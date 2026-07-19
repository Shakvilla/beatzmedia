# WU-CAT-9 — Collaborator split invite/accept flow (design)

**Status:** design approved 2026-07-19 · **Module:** catalog (+ notifications for email delivery) · **Depends on:** WU-CAT-6 (split persistence)

## Goal

Let an artist who has *chosen* to share a release's earnings invite each collaborator to **accept or decline** their per-track royalty split. Accepting links the split to the collaborator's real BeatzClik account (so a later payments WU can route their share into their own studio payout balance); declining releases that share back to the creator. Backend-only — the accept page + Splits-step UI are a separate frontend follow-up.

## Context & framing

- **Splits are already optional by construction.** A `SplitEntry` row exists only if the artist deliberately adds a collaborator. The common case — Artist A pays Artist B for a feature and A owns the song — is simply *no split rows*, and the creator implicitly keeps 100%. Nothing in this WU changes that; the flow engages *only* when the artist has chosen to share.
- **A confirmed collaborator earns real money they must withdraw**, and withdrawal happens through the artist studio + payouts, which needs a real account (you can't pay out to a bare email). Therefore a *confirmed* split cannot stay attached to only free-text `email`/`name` — it must carry the collaborator's `accountId`. This is why acceptance is **account-linked**.
- WU-CAT-6 shipped splits as `pending` with collaborators identified by free-text `email`/`name` (no account link, no token, no `declined` state). The catalog ADD §9 explicitly deferred "invite emails/notifications, a tokenized accept/decline endpoint, resend, and a `declined` state" to a follow-on WU. **This is that WU.**

## Locked decisions (from brainstorming)

1. **Account-linked accept.** To accept, the collaborator must be a logged-in BeatzClik account; accepting stamps their `accountId` onto the split and flips it to `confirmed`. (Not anonymous-token accept; not auto-provision-artist.)
2. **Invites fire on release submit** (`draft → in_review`) — the deliberate "I'm done" action — not on every autosave PATCH. A resend endpoint covers misses/expiry.
3. **This WU touches identity zero times.** Accept stores `jwt.getSubject()` as the `accountId` string (catalog already reads the JWT subject for the acting artist). No identity port, no email→account lookup, no auto-provisioning.
4. **No in-app-notification bonus** for already-registered collaborators in v1 (would require a new identity lookup port for marginal value). Email works for everyone.
5. **No frontend** in this WU.

---

## 1. Domain & data model

### 1.1 `SplitEntry` changes (catalog domain)

- **New field `accountId`** — nullable. Set only on accept; the collaborator's BeatzClik account id. Stored as a bare `TEXT` column with **no foreign key** (the golden no-cross-module-FK rule forbids referencing identity's `account` table).
- **`SplitConfirmation` gains `declined`.** Enum becomes `self | confirmed | pending | auto | declined`. The domain constructor/reconstitute paths accept the new value; the `split_entry.confirmation` CHECK constraint is widened.

Backward compatibility: reconstitute overloads follow the WU-CAT-6 precedent (add a new overload carrying `accountId`; existing call sites default it to `null`).

### 1.2 New `SplitInvite` domain type + `split_invite` table (catalog-owned)

Mirrors identity's `PasswordResetToken` shape (single-use, time-boxed, hash-only storage).

**Domain `SplitInvite`** (framework-free record):
- `id` (UUIDv7 via `IdGenerator`)
- `releaseId`
- `email` (the invited collaborator address, as typed by the artist on the split)
- `tokenHash` (SHA-256 hex of the opaque plaintext token — **plaintext is never persisted**)
- `expiresAt`
- `consumedAt` (nullable)
- `outcome` (nullable enum `accepted | declined`)
- `createdAt`

Behavior on the aggregate: `isExpired(now)`, `isConsumed()`, `consume(outcome, now)` (sets `consumedAt` + `outcome`; rejects double-consume).

**Table `split_invite`** (migration **V971** — re-confirm the next-free number with `backend/scripts/next-migration-version.sh` at build time; V970 is the latest catalog file today):

```sql
CREATE TABLE split_invite (
    id           TEXT PRIMARY KEY,
    release_id   TEXT NOT NULL REFERENCES release(id) ON DELETE CASCADE,
    email        TEXT NOT NULL,
    token_hash   TEXT NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ NOT NULL,
    consumed_at  TIMESTAMPTZ,
    outcome      TEXT CHECK (outcome IN ('accepted','declined')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_split_invite_release ON split_invite(release_id);

ALTER TABLE split_entry ADD COLUMN account_id TEXT;   -- nullable, no FK; set on accept
ALTER TABLE split_entry DROP CONSTRAINT IF EXISTS split_entry_confirmation_check;
ALTER TABLE split_entry ADD CONSTRAINT split_entry_confirmation_check
    CHECK (confirmation IN ('self','confirmed','pending','auto','declined'));
```

(Exact existing constraint name to be confirmed against `V305__catalog_releases.sql` at build time; the WU-CAT-6 note there is authoritative.)

### 1.3 Grouping rule — one invite per `(release, email)`

A collaborator featured on multiple tracks of a release gets **one** invite/token, and accepting confirms **all** of that email's pending split rows on that release in a single action. Rationale: less email spam, matches intuition ("Artist A wants to split this release with you"). The accept page lists each track + percent + role for that collaborator.

**TTL:** configurable `beatz.catalog.split-invite-ttl-seconds`, default **1209600** (14 days). The release sits in admin review, so a reset-style 30-minute window would be wrong.

---

## 2. State machine & the go-live guard

- Collaborator split lands `pending` (unchanged, WU-CAT-6).
- **Accept:** `pending → confirmed` for every one of that collaborator's pending rows on the release, and stamps `accountId = jwt.subject` on each.
- **Decline:** `pending → declined`. The creator keeps that share via the existing implicit-remainder model — **no redistribution logic** needed.
- **Go-live guard is unchanged and already correct.** `CatalogRepository.hasPendingSplits(ReleaseId)` blocks `→ live` only while a split is `pending`; `confirmed` and `declined` are both terminal and non-blocking. So a declined collaborator no longer holds up the release, and an expired-but-unaccepted invite keeps the split `pending` (release blocked) until the artist resends or removes that collaborator via PATCH.

Only `pending` rows are ever acted on by accept/decline; already-`confirmed`/`declined` rows are left untouched (idempotent replay).

---

## 3. Endpoints

### 3.1 Public accept surface — new `SplitInviteResource` at `/v1/splits/invites`

Mirrors the `@PermitAll` password-reset precedent (`MeResource` / `AuthResource`).

| Method | Auth | Purpose |
|---|---|---|
| `GET /v1/splits/invites/{token}` | `@PermitAll` | Renders the accept page **before** login. Returns `SplitInviteView` (§3.3). Looks up by `sha256(token)`; if not found → 404; if consumed/expired, still returns the view with the appropriate `status` so the page can show "already accepted"/"expired". |
| `POST /v1/splits/invites/{token}/accept` | `@Authenticated` | Collaborator must be logged in. Verify token (found → not expired → not consumed, else 410 `GONE` / 404). Flip the collaborator's pending rows to `confirmed`, stamp `accountId = jwt.subject`, `consume(accepted)`. Single-use ⇒ naturally idempotent; a second accept on a consumed token → 410 `GONE`. |
| `POST /v1/splits/invites/{token}/decline` | `@PermitAll` | Decline **without** an account. Flip pending rows to `declined`, `consume(declined)`. |

### 3.2 Artist surface — one method on existing `StudioReleaseResource` (`@RolesAllowed("artist")`)

| Method | Purpose |
|---|---|
| `POST /v1/studio/releases/{id}/resend-invites` | Owning-artist-only (release `artistId` must equal `jwt.subject`, else 403/404). For every split still `pending`, mint a **fresh** `SplitInvite` (new token; the prior unconsumed invite for that `(release,email)` is invalidated/replaced) and re-fire `SplitInviteIssued`. Returns 204. |

### 3.3 `SplitInviteView` (contract shape for the accept page)

```
SplitInviteView {
  status: "pending" | "accepted" | "declined" | "expired",
  artistName: string,
  releaseTitle: string,
  tracks: [ { trackTitle: string, role: string, percent: int } ]
}
```

### 3.4 Cross-cutting

- Every mutation (accept/decline/resend) appends an **AuditEntry** (INV-10).
- **Trust model:** the opaque token is the bearer credential — possession proves it reached the invited inbox (same model as password reset), bounded by the 14-day TTL + single-use consumption. The logged-in account's email is **not** required to match the invited email (a collaborator may log in under a different address). Accepted as a documented residual risk in the ADR.
- **Idempotency:** single-use token consumption is the idempotency mechanism for accept/decline. Resend is intentionally repeatable (mints a new token each call).

---

## 4. Invite trigger & email path (hexagonal-clean)

### 4.1 Trigger — on submit

In the release **submit** service (the `draft → in_review` transition), after the transition succeeds and within the same transaction/audit boundary:
1. Load the release's pending splits, grouped by collaborator `email`.
2. For each group, generate a plaintext token (`idGenerator.newId() + idGenerator.newId()`), compute `tokenHash = sha256Hex(plaintext)`, build the `SplitInvite` with `expiresAt = now + ttl`, and persist it.
3. Build `acceptUrl = {beatz.catalog.split-invite-accept-base-url}?token={plaintext}` (catalog owns the token, so it builds the URL; plaintext lives only in the in-process event and is then discarded).
4. Fire a CDI domain event **`SplitInviteIssued`** carrying `{ email, acceptUrl, artistName, releaseTitle, trackSummaries: [{trackTitle, role, percent}] }`.

Re-submit is prevented by the FSM (`draft → in_review` is one-way), so trigger runs once; resend covers subsequent sends.

### 4.2 Delivery — notifications module

A new **`SplitInviteObserver`** in the notifications module reacts to `SplitInviteIssued` (`@Observes(during = AFTER_SUCCESS)` + `@Transactional(REQUIRES_NEW)`, mirroring the tips→notification observer). It composes an `EmailMessage` (subject + body containing the accept link and the per-track summary) and sends it via the existing `Mailer` output port to the **raw `email` address** — a small new transactional-email path that bypasses the `AccountId`-gated `NotificationContactPort` resolution, because a collaborator may not be a BeatzClik user.

**Seam:** catalog never touches `Mailer`; notifications never touches split state. Reuses the one platform mailer (Mailpit in dev). No new identity dependency.

---

## 5. Files (indicative)

**Catalog:**
- `domain/SplitEntry.java` — add `accountId`; reconstitute overload.
- `domain/SplitConfirmation.java` — add `declined`.
- `domain/SplitInvite.java` — new aggregate (issue/isExpired/consume).
- `domain/SplitInviteIssued.java` — new domain event.
- `application/port/out/CatalogRepository.java` (+ JPA impl + `FakeCatalogRepository`) — `saveSplitInvite`, `findSplitInviteByHash`, `consumeSplitInvite` (or save-updated), `pendingSplitEmailsForRelease(releaseId)`, `confirmSplitsForReleaseEmail(releaseId, email, accountId)`, `declineSplitsForReleaseEmail(releaseId, email)`, plus a read for the invite view (`findReleaseSummaryForInvite`).
- `application/port/in/` — `AcceptSplitInvite`, `DeclineSplitInvite`, `GetSplitInvite`, `ResendSplitInvites` (or fold onto existing studio port) + `SplitInviteView`.
- `application/service/` — new services for the above; submit-service hook to issue invites + fire event.
- `adapter/in/rest/SplitInviteResource.java` — new `@PermitAll`-based public resource (3 endpoints).
- `adapter/in/rest/StudioReleaseResource.java` — `resend-invites` method.
- `adapter/out/persistence/` — `SplitInviteEntity` + mappings.
- `src/main/resources/db/migration/V971__catalog_split_invite.sql`.

**Notifications:**
- `adapter/in/events/SplitInviteObserver.java` — new observer.
- raw-address email path (new small method/port usage on the existing `Mailer` seam; add an `EmailMessage` builder for the invite).

**Config:** `application.properties` — `beatz.catalog.split-invite-ttl-seconds`, `beatz.catalog.split-invite-accept-base-url` (blank/dev default; documented, non-secret).

**Docs:** catalog ADD §9 as-built update; new ADR (token trust model + no-email-match residual risk); `API-CONTRACT.md` (4 endpoints + `SplitInviteView`); register **WU-CAT-9** in `backend/.project/backlog.yaml` (`depends_on: [WU-CAT-6]`).

---

## 6. Testing

- **Unit:** token issue/verify/expire/consume; `SplitInvite.consume` rejects double-consume; accept links `accountId` + confirms *all* the collaborator's release rows in one call; decline sets `declined`; go-live still blocked while any split `pending`, unblocked once none pending (confirmed+declined mix); grouping (multi-track collaborator → one invite, one accept confirms all).
- **Integration (Testcontainers + Mailpit):** submit → invite(s) persisted + `SplitInviteIssued` fired → email sent to the raw address; accept round-trip through the real REST resource links the account + confirms rows; decline round-trip; resend mints a fresh token and invalidates the old; expired token → 410 `GONE`; consumed token re-accept → 410 `GONE`; `GET /{token}` renders the view pre-login (`@PermitAll`).
- **Contract:** `SplitInviteView` shape (present/nullable fields) and the 4 endpoints match `API-CONTRACT.md`.
- **ArchUnit:** domain stays framework-free; catalog does not import notifications/identity internals; the email send lives only in notifications.

## 7. Definition of Done

Unit + integration + contract tests pass; ArchUnit green; Flyway V971 applies on an empty DB; `docker compose up` boots healthy; coverage gate met; Spotless clean; catalog ADD + ADR + API-CONTRACT updated in the same PR; `verify.sh` + `smoke.sh` green (user-run); WU-CAT-9 registered in `backlog.yaml`.

## 8. Out of scope (explicit)

- Frontend accept page + Splits-step wiring (separate frontend WU).
- Routing a confirmed collaborator's share into their ledger/payout balance at settlement (a payments/royalty WU — OQ-4 / WU-PAY-3 territory).
- Collaborator auto-upgrade to artist / KYC (the collaborator does this themselves when they choose to withdraw).
- In-app notification for already-registered collaborators (deferred; email suffices).
