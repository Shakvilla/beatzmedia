# WU-CAT-9 — Collaborator split invite/accept Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an artist who chose to share a release's earnings invite each collaborator (by email, on release submit) to accept or decline their per-track royalty split; accepting links the split to the collaborator's real BeatzClik account and clears the go-live block, declining releases the share back to the creator.

**Architecture:** Backend-only, catalog-owned, mirroring identity's password-reset token model. Splits already land `pending` (WU-CAT-6). This WU adds a `SplitInvite` token aggregate + `split_invite` table, an `accountId` + `declined` state on `SplitEntry`, three public token endpoints (`GET`/`accept`/`decline`) + an artist `resend-invites` endpoint, an invite-issuance hook in the submit service that fires a `SplitInviteIssued` domain event, and a notifications observer that emails the invite to the raw address. Catalog never touches `Mailer`; notifications never touches split state; identity is untouched.

**Tech Stack:** Java 25, Quarkus 3.37, Hibernate ORM/Panache (plain JPA `EntityManager`), PostgreSQL 16 + Flyway, JAX-RS (`@RolesAllowed`/`@PermitAll` + `JsonWebToken`), CDI events (`AFTER_SUCCESS` + `REQUIRES_NEW`), Testcontainers (Postgres 18) + Mailpit for ITs, JUnit 5.

## Global Constraints

- **Branch:** `feat/WU-CAT-9-collaborator-invite-accept` (already created, stacked on `feat/WU-CAT-6-split-persistence`; spec committed). One WU per branch. Conventional Commits with `(catalog)` scope + WU id.
- **Never stage or commit** `backend/src/main/resources/application.properties` or `backend/docker-compose.yml` (they carry a real local dev DB password). All new config uses `@ConfigProperty(defaultValue=...)` in code — **do not** add keys to `application.properties`.
- **Hexagonal dependency rule:** `adapter → application → domain`. Domain imports no framework (no Jakarta/Quarkus/Hibernate). Inbound/outbound adapters never import each other. Catalog must not read another module's tables or call another module's ports; cross-module contact is via domain events only. **No cross-module FK** — `split_entry.account_id` is a bare `TEXT` (no reference to identity's `account`).
- **INV-12:** a release cannot transition to `live` while any split is `pending`. `confirmed`/`declined` are terminal and non-blocking. `hasPendingSplits` already enforces this and must keep working unchanged.
- **INV-10:** every privileged mutation (accept/decline/resend) appends an `AuditEntry` in the same transaction.
- **Token security:** only the SHA-256 hex hash of the opaque token is persisted; plaintext is generated, emailed, and discarded — never stored or logged (mirror `PasswordResetToken` / `RequestPasswordResetService`).
- **Migrations:** forward-only `V<n>__<desc>.sql`. Allocate the next version with `bash backend/scripts/next-migration-version.sh` at build time (this plan assumes **V971**; re-confirm and rename if it drifted).
- **Per-task test gate:** targeted `cd backend && ./mvnw -q test -Dtest=<Class>` (unit) / `-Dtest=<Class>IT` (integration). The **full** `bash backend/scripts/verify.sh && bash backend/scripts/smoke.sh` is run by the **USER** at the final task (do not run it yourself — IntelliJ JPS races the build).
- **DoD:** unit + integration + contract tests pass; ArchUnit green; Flyway applies on an empty DB; `docker compose up` boots healthy; coverage gate met; Spotless clean; catalog ADD + ADR + API-CONTRACT updated in the same PR.

## File Structure

**New (catalog domain):** `SplitInvite.java`, `InviteOutcome.java`, `SplitInviteIssued.java`, `SplitInviteNotFoundException.java`, `SplitInviteGoneException.java`.
**New (catalog application):** `port/in/GetSplitInvite.java`, `port/in/AcceptSplitInvite.java`, `port/in/DeclineSplitInvite.java`, `port/in/ResendSplitInvites.java`, `port/in/SplitInviteView.java`, `service/SplitInviteService.java` (implements the four ports).
**New (catalog adapter):** `adapter/in/rest/SplitInviteResource.java`, `adapter/out/persistence/SplitInviteEntity.java`.
**New (catalog resource):** `src/main/resources/db/migration/V971__catalog_split_invite.sql`.
**New (notifications adapter):** `adapter/in/events/SplitInviteEmailObserver.java`.
**Modified (catalog):** `domain/SplitConfirmation.java` (+`declined`), `domain/SplitEntry.java` (+`accountId`), `application/port/out/CatalogRepository.java` (+7 methods), `adapter/out/persistence/JpaCatalogRepository.java` (impl + `accountId` in split load/save), `adapter/out/persistence/SplitEntryEntity.java` (+`accountId`), `application/service/FinalizeReleaseService.java` (issue invites + fire event), `adapter/in/rest/StudioReleaseResource.java` (+`resend-invites`).
**Modified (platform):** `domain/ErrorCode.java` (+2 codes), `adapter/in/rest/DomainExceptionMapper.java` (+mappings, +410 GONE).
**Modified (test):** `catalog/fakes/FakeCatalogRepository.java` (+7 methods + accessors).
**Docs:** `backend/docs/architecture/catalog.md`, `backend/docs/00-system-architecture.md` (ADR), `API-CONTRACT.md`, `backend/.project/backlog.yaml`.

---

## Task 1: Domain model + error codes

**Files:**
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/domain/SplitConfirmation.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/domain/SplitEntry.java`
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/domain/InviteOutcome.java`
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/domain/SplitInvite.java`
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/domain/SplitInviteIssued.java`
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/domain/SplitInviteNotFoundException.java`
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/domain/SplitInviteGoneException.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/platform/domain/ErrorCode.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/platform/adapter/in/rest/DomainExceptionMapper.java`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/catalog/domain/SplitInviteTest.java`

**Interfaces:**
- Produces (used by Tasks 2–5):
  - `enum SplitConfirmation { self, confirmed, pending, auto, declined }`
  - `record SplitEntry(String id, String trackId, String name, String email, String role, int percent, SplitConfirmation confirmation, String accountId)` + 7-arg convenience ctor delegating `accountId = null`.
  - `enum InviteOutcome { accepted, declined }`
  - `final class SplitInvite` with `issue(id, releaseId, email, tokenHash, expiresAt, createdAt)`, `reconstitute(...)`, `isExpired(now)`, `isConsumed()`, `consume(outcome, at)`, getters `id()/releaseId()/email()/tokenHash()/expiresAt()/consumedAt()/outcome()/createdAt()`.
  - `record SplitInviteIssued(String email, String acceptUrl, String artistName, String releaseTitle, List<TrackShare> trackShares)` with nested `record TrackShare(String trackTitle, String role, int percent)`.
  - `SplitInviteNotFoundException` → `ErrorCode.SPLIT_INVITE_NOT_FOUND` (404); `SplitInviteGoneException` → `ErrorCode.SPLIT_INVITE_GONE` (410).

- [ ] **Step 1: Write the failing test** — `SplitInviteTest.java`

```java
package org.shakvilla.beatzmedia.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SplitInviteTest {

  private static final Instant T0 = Instant.parse("2026-07-19T00:00:00Z");

  @Test
  void issue_isPendingAndUnconsumed() {
    SplitInvite invite = SplitInvite.issue("inv-1", "rel-1", "bob@x.com", "hash", T0.plusSeconds(3600), T0);
    assertThat(invite.isConsumed()).isFalse();
    assertThat(invite.isExpired(T0)).isFalse();
    assertThat(invite.outcome()).isNull();
    assertThat(invite.consumedAt()).isNull();
  }

  @Test
  void isExpired_trueAtOrAfterExpiry() {
    SplitInvite invite = SplitInvite.issue("inv-1", "rel-1", "bob@x.com", "hash", T0.plusSeconds(10), T0);
    assertThat(invite.isExpired(T0.plusSeconds(9))).isFalse();
    assertThat(invite.isExpired(T0.plusSeconds(10))).isTrue();
  }

  @Test
  void consume_setsOutcomeAndTimestamp() {
    SplitInvite invite = SplitInvite.issue("inv-1", "rel-1", "bob@x.com", "hash", T0.plusSeconds(3600), T0);
    invite.consume(InviteOutcome.accepted, T0.plusSeconds(5));
    assertThat(invite.isConsumed()).isTrue();
    assertThat(invite.outcome()).isEqualTo(InviteOutcome.accepted);
    assertThat(invite.consumedAt()).isEqualTo(T0.plusSeconds(5));
  }

  @Test
  void consume_rejectsDoubleConsume() {
    SplitInvite invite = SplitInvite.issue("inv-1", "rel-1", "bob@x.com", "hash", T0.plusSeconds(3600), T0);
    invite.consume(InviteOutcome.accepted, T0.plusSeconds(5));
    assertThatThrownBy(() -> invite.consume(InviteOutcome.declined, T0.plusSeconds(6)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void splitEntry_sevenArgCtor_defaultsAccountIdNull() {
    SplitEntry e = new SplitEntry("s1", "t1", "Bob", "bob@x.com", "producer", 20, SplitConfirmation.pending);
    assertThat(e.accountId()).isNull();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -q test -Dtest=SplitInviteTest`
Expected: FAIL — compilation errors (`SplitInvite`, `InviteOutcome` do not exist; `SplitEntry` has no `accountId`).

- [ ] **Step 3: Widen `SplitConfirmation`**

```java
package org.shakvilla.beatzmedia.catalog.domain;

/** Confirmation state for a revenue split entry. Catalog ADD §3. */
public enum SplitConfirmation {
  self, confirmed, pending, auto, declined
}
```

- [ ] **Step 4: Add `accountId` to `SplitEntry`** (canonical 8-arg + 7-arg convenience)

```java
package org.shakvilla.beatzmedia.catalog.domain;

/**
 * Revenue split allocation for a track. Sum of split percents on a track must be ≤ 100 (INV-12).
 * {@code accountId} links a collaborator's confirmed split to their BeatzClik account (WU-CAT-9);
 * null while pending/declined or before accept. Domain value object; no framework imports.
 */
public record SplitEntry(
    String id,
    String trackId,
    String name,
    String email,
    String role,
    int percent,
    SplitConfirmation confirmation,
    String accountId) {

  /** Legacy 7-arg form — no linked account yet (accountId == null). */
  public SplitEntry(String id, String trackId, String name, String email, String role,
      int percent, SplitConfirmation confirmation) {
    this(id, trackId, name, email, role, percent, confirmation, null);
  }
}
```

- [ ] **Step 5: Create `InviteOutcome`, `SplitInvite`, `SplitInviteIssued`**

`InviteOutcome.java`:
```java
package org.shakvilla.beatzmedia.catalog.domain;

/** Terminal outcome of a collaborator split invite (WU-CAT-9). */
public enum InviteOutcome {
  accepted, declined
}
```

`SplitInvite.java`:
```java
package org.shakvilla.beatzmedia.catalog.domain;

import java.time.Instant;

/**
 * Single-use, time-boxed collaborator split invite (WU-CAT-9). Mirrors identity's
 * {@code PasswordResetToken}: only the SHA-256 hash of the opaque token is persisted — never the
 * plaintext. One invite covers all of a collaborator's pending splits on one release.
 */
public final class SplitInvite {

  private final String id;
  private final String releaseId;
  private final String email;
  private final String tokenHash;
  private final Instant expiresAt;
  private Instant consumedAt;
  private InviteOutcome outcome;
  private final Instant createdAt;

  private SplitInvite(String id, String releaseId, String email, String tokenHash,
      Instant expiresAt, Instant consumedAt, InviteOutcome outcome, Instant createdAt) {
    this.id = id;
    this.releaseId = releaseId;
    this.email = email;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.consumedAt = consumedAt;
    this.outcome = outcome;
    this.createdAt = createdAt;
  }

  /** Factory for a freshly issued, unconsumed invite. */
  public static SplitInvite issue(String id, String releaseId, String email, String tokenHash,
      Instant expiresAt, Instant createdAt) {
    return new SplitInvite(id, releaseId, email, tokenHash, expiresAt, null, null, createdAt);
  }

  /** Rehydrate from persistence. */
  public static SplitInvite reconstitute(String id, String releaseId, String email,
      String tokenHash, Instant expiresAt, Instant consumedAt, InviteOutcome outcome,
      Instant createdAt) {
    return new SplitInvite(id, releaseId, email, tokenHash, expiresAt, consumedAt, outcome, createdAt);
  }

  public boolean isExpired(Instant now) {
    return !now.isBefore(expiresAt);
  }

  public boolean isConsumed() {
    return consumedAt != null;
  }

  /** Marks the invite consumed with a terminal outcome. Rejects a second consume. */
  public void consume(InviteOutcome outcome, Instant at) {
    if (consumedAt != null) {
      throw new IllegalStateException("Split invite already consumed: " + id);
    }
    this.outcome = outcome;
    this.consumedAt = at;
  }

  public String id() { return id; }
  public String releaseId() { return releaseId; }
  public String email() { return email; }
  public String tokenHash() { return tokenHash; }
  public Instant expiresAt() { return expiresAt; }
  public Instant consumedAt() { return consumedAt; }
  public InviteOutcome outcome() { return outcome; }
  public Instant createdAt() { return createdAt; }
}
```

`SplitInviteIssued.java`:
```java
package org.shakvilla.beatzmedia.catalog.domain;

import java.util.List;

/**
 * Domain event fired by catalog when a collaborator's split invite is issued on release submit
 * (WU-CAT-9). Observed by the notifications module, which emails {@code acceptUrl} to {@code email}.
 * Framework-free; the sole cross-module channel (hexagonal rule — no table reads).
 */
public record SplitInviteIssued(
    String email,
    String acceptUrl,
    String artistName,
    String releaseTitle,
    List<TrackShare> trackShares) {

  /** One collaborator share line for the invite email/accept page. */
  public record TrackShare(String trackTitle, String role, int percent) {}
}
```

- [ ] **Step 6: Add error codes + mapper cases + catalog exceptions**

In `platform/domain/ErrorCode.java`, add two enum constants (place near the other catalog codes, e.g. after `SPLIT_OVER_100`): `SPLIT_INVITE_NOT_FOUND`, `SPLIT_INVITE_GONE`.

In `platform/adapter/in/rest/DomainExceptionMapper.java`:
- Add `SPLIT_INVITE_NOT_FOUND` to the `NOT_FOUND` `case` group (the arm returning `Response.Status.NOT_FOUND.getStatusCode()`).
- Add a new arm before `INTERNAL`: `case SPLIT_INVITE_GONE -> Response.Status.GONE.getStatusCode();`

`SplitInviteNotFoundException.java`:
```java
package org.shakvilla.beatzmedia.catalog.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/** Thrown when a split-invite token does not resolve. Maps to 404 SPLIT_INVITE_NOT_FOUND. */
public class SplitInviteNotFoundException extends DomainException {
  public SplitInviteNotFoundException() {
    super(ErrorCode.SPLIT_INVITE_NOT_FOUND, "Split invite not found");
  }
}
```

`SplitInviteGoneException.java`:
```java
package org.shakvilla.beatzmedia.catalog.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/** Thrown when a split invite is already consumed or expired. Maps to 410 SPLIT_INVITE_GONE. */
public class SplitInviteGoneException extends DomainException {
  public SplitInviteGoneException(String message) {
    super(ErrorCode.SPLIT_INVITE_GONE, message);
  }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `cd backend && ./mvnw -q test -Dtest=SplitInviteTest`
Expected: PASS (5 tests). Also compile-check the module: `cd backend && ./mvnw -q -o compile` → BUILD SUCCESS (confirms the `SplitEntry` 7-arg convenience ctor keeps `UpdateReleaseService`/`JpaCatalogRepository` compiling and the `DomainExceptionMapper` switch stays exhaustive).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/org/shakvilla/beatzmedia/catalog/domain \
        backend/src/main/java/org/shakvilla/beatzmedia/platform/domain/ErrorCode.java \
        backend/src/main/java/org/shakvilla/beatzmedia/platform/adapter/in/rest/DomainExceptionMapper.java \
        backend/src/test/java/org/shakvilla/beatzmedia/catalog/domain/SplitInviteTest.java
git commit -m "feat(catalog): WU-CAT-9 split-invite domain + SplitEntry.accountId + declined state"
```

---

## Task 2: Persistence — migration, entities, repository methods

**Files:**
- Create: `backend/src/main/resources/db/migration/V971__catalog_split_invite.sql`
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/out/persistence/SplitInviteEntity.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/out/persistence/SplitEntryEntity.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/port/out/CatalogRepository.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/out/persistence/JpaCatalogRepository.java`
- Modify: `backend/src/test/java/org/shakvilla/beatzmedia/catalog/fakes/FakeCatalogRepository.java`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/catalog/it/SplitInvitePersistenceIT.java`

**Interfaces:**
- Consumes (Task 1): `SplitInvite`, `InviteOutcome`, `SplitEntry(...accountId)`, `SplitConfirmation.declined`.
- Produces (Task 3) — new `CatalogRepository` methods:
  - `void saveSplitInvite(SplitInvite invite)`
  - `Optional<SplitInvite> findSplitInviteByHash(String tokenHash)`
  - `void consumeSplitInvite(String tokenHash, InviteOutcome outcome, Instant at)`
  - `List<String> pendingSplitEmailsForRelease(ReleaseId releaseId)`
  - `void confirmSplitsForReleaseEmail(ReleaseId releaseId, String email, String accountId)`
  - `void declineSplitsForReleaseEmail(ReleaseId releaseId, String email)`
  - `void deleteUnconsumedInvitesForReleaseEmail(ReleaseId releaseId, String email)`

- [ ] **Step 1: Confirm migration version, then write the migration**

Run `bash backend/scripts/next-migration-version.sh` — if it prints a number other than `971`, rename the file accordingly. Create `V971__catalog_split_invite.sql`:

```sql
-- WU-CAT-9: collaborator split invite/accept.
-- split_invite mirrors identity's password_reset_token (hash-only, single-use, time-boxed).
CREATE TABLE split_invite (
    id          TEXT PRIMARY KEY,
    release_id  TEXT NOT NULL REFERENCES release(id) ON DELETE CASCADE,
    email       TEXT NOT NULL,
    token_hash  TEXT NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    outcome     TEXT CHECK (outcome IN ('accepted','declined')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_split_invite_release ON split_invite(release_id);

-- Link a confirmed collaborator split to their account (no cross-module FK); widen states.
ALTER TABLE split_entry ADD COLUMN account_id TEXT;
ALTER TABLE split_entry DROP CONSTRAINT split_entry_confirmation_check;
ALTER TABLE split_entry ADD CONSTRAINT split_entry_confirmation_check
    CHECK (confirmation IN ('self','confirmed','pending','auto','declined'));
```

Verify the existing constraint name first: `grep -n "confirmation" backend/src/main/resources/db/migration/V305__catalog_releases.sql`. If the CHECK is inline/unnamed, Postgres auto-named it `split_entry_confirmation_check` (table + column + `_check`); the `DROP CONSTRAINT` above assumes that. If the grep shows a different explicit name, use it.

- [ ] **Step 2: Add `accountId` to `SplitEntryEntity`**

In `SplitEntryEntity.java`, add after the `percent` field and update the confirmation doc comment:
```java
  @Column(name = "account_id")
  public String accountId;
```
Change the `confirmation` javadoc to `/** Values: self | confirmed | pending | auto | declined */`.

- [ ] **Step 3: Create `SplitInviteEntity`**

```java
package org.shakvilla.beatzmedia.catalog.adapter.out.persistence;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA entity for {@code split_invite} (WU-CAT-9 / V971). */
@Entity
@Table(name = "split_invite")
public class SplitInviteEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "release_id", nullable = false)
  public String releaseId;

  @Column(name = "email", nullable = false)
  public String email;

  @Column(name = "token_hash", nullable = false, unique = true)
  public String tokenHash;

  @Column(name = "expires_at", nullable = false)
  public Instant expiresAt;

  @Column(name = "consumed_at")
  public Instant consumedAt;

  /** Values: accepted | declined (null while pending). */
  @Column(name = "outcome")
  public String outcome;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
```

- [ ] **Step 4: Add the 7 methods to `CatalogRepository`** (port interface)

Add imports `org.shakvilla.beatzmedia.catalog.domain.SplitInvite`, `org.shakvilla.beatzmedia.catalog.domain.InviteOutcome`, `java.util.Optional` (if absent), and after the existing `hasPendingSplits` declaration:

```java
  // ---- WU-CAT-9: collaborator split invite/accept ----

  /** Persist a freshly issued split invite. */
  void saveSplitInvite(SplitInvite invite);

  /** Look up a split invite by its token's SHA-256 hash. */
  Optional<SplitInvite> findSplitInviteByHash(String tokenHash);

  /** Mark an invite consumed with a terminal outcome. */
  void consumeSplitInvite(String tokenHash, InviteOutcome outcome, Instant at);

  /** Distinct collaborator emails with at least one {@code pending} split on this release. */
  List<String> pendingSplitEmailsForRelease(ReleaseId releaseId);

  /** Flip this collaborator's {@code pending} splits on the release to {@code confirmed} + link account. */
  void confirmSplitsForReleaseEmail(ReleaseId releaseId, String email, String accountId);

  /** Flip this collaborator's {@code pending} splits on the release to {@code declined}. */
  void declineSplitsForReleaseEmail(ReleaseId releaseId, String email);

  /** Delete any not-yet-consumed invites for this collaborator on the release (resend replaces them). */
  void deleteUnconsumedInvitesForReleaseEmail(ReleaseId releaseId, String email);
```

- [ ] **Step 5: Implement them in `JpaCatalogRepository`** + carry `accountId` through split load/save

Update `loadSplitsForTracks` mapping (currently `new SplitEntry(s.id, s.trackId, s.name, s.email, s.role, s.percent, SplitConfirmation.valueOf(s.confirmation))`) to the 8-arg form:
```java
        .map(s -> new SplitEntry(s.id, s.trackId, s.name, s.email, s.role, s.percent,
            SplitConfirmation.valueOf(s.confirmation), s.accountId))
```
In `saveTrackSplits`, set `e.accountId = s.accountId();` after `e.confirmation = s.confirmation().name();`.

Add the new method bodies (mirroring the `hasPendingSplits` track→release join):
```java
  @Override
  public void saveSplitInvite(SplitInvite invite) {
    SplitInviteEntity e = new SplitInviteEntity();
    e.id = invite.id();
    e.releaseId = invite.releaseId();
    e.email = invite.email();
    e.tokenHash = invite.tokenHash();
    e.expiresAt = invite.expiresAt();
    e.consumedAt = invite.consumedAt();
    e.outcome = invite.outcome() == null ? null : invite.outcome().name();
    e.createdAt = invite.createdAt();
    em.persist(e);
  }

  @Override
  public Optional<SplitInvite> findSplitInviteByHash(String tokenHash) {
    List<SplitInviteEntity> rows = em.createQuery(
            "SELECT i FROM SplitInviteEntity i WHERE i.tokenHash = :h", SplitInviteEntity.class)
        .setParameter("h", tokenHash)
        .getResultList();
    if (rows.isEmpty()) return Optional.empty();
    SplitInviteEntity e = rows.get(0);
    return Optional.of(SplitInvite.reconstitute(e.id, e.releaseId, e.email, e.tokenHash,
        e.expiresAt, e.consumedAt, e.outcome == null ? null : InviteOutcome.valueOf(e.outcome),
        e.createdAt));
  }

  @Override
  public void consumeSplitInvite(String tokenHash, InviteOutcome outcome, Instant at) {
    em.createQuery(
            "UPDATE SplitInviteEntity i SET i.consumedAt = :at, i.outcome = :o WHERE i.tokenHash = :h")
        .setParameter("at", at)
        .setParameter("o", outcome.name())
        .setParameter("h", tokenHash)
        .executeUpdate();
  }

  @Override
  public List<String> pendingSplitEmailsForRelease(ReleaseId releaseId) {
    List<String> trackIds = releaseTrackIds(releaseId.value());
    if (trackIds.isEmpty()) return List.of();
    return em.createQuery(
            "SELECT DISTINCT s.email FROM SplitEntryEntity s "
                + "WHERE s.trackId IN :trackIds AND s.confirmation = 'pending'", String.class)
        .setParameter("trackIds", trackIds)
        .getResultList();
  }

  @Override
  public void confirmSplitsForReleaseEmail(ReleaseId releaseId, String email, String accountId) {
    List<String> trackIds = releaseTrackIds(releaseId.value());
    if (trackIds.isEmpty()) return;
    em.createQuery(
            "UPDATE SplitEntryEntity s SET s.confirmation = 'confirmed', s.accountId = :acc "
                + "WHERE s.trackId IN :trackIds AND s.email = :email AND s.confirmation = 'pending'")
        .setParameter("acc", accountId)
        .setParameter("trackIds", trackIds)
        .setParameter("email", email)
        .executeUpdate();
  }

  @Override
  public void declineSplitsForReleaseEmail(ReleaseId releaseId, String email) {
    List<String> trackIds = releaseTrackIds(releaseId.value());
    if (trackIds.isEmpty()) return;
    em.createQuery(
            "UPDATE SplitEntryEntity s SET s.confirmation = 'declined' "
                + "WHERE s.trackId IN :trackIds AND s.email = :email AND s.confirmation = 'pending'")
        .setParameter("trackIds", trackIds)
        .setParameter("email", email)
        .executeUpdate();
  }

  @Override
  public void deleteUnconsumedInvitesForReleaseEmail(ReleaseId releaseId, String email) {
    em.createQuery(
            "DELETE FROM SplitInviteEntity i "
                + "WHERE i.releaseId = :rid AND i.email = :email AND i.consumedAt IS NULL")
        .setParameter("rid", releaseId.value())
        .setParameter("email", email)
        .executeUpdate();
  }

  /** Track ids belonging to a release (same join hasPendingSplits uses). */
  private List<String> releaseTrackIds(String releaseId) {
    return em.createQuery(
            "SELECT rt.trackId FROM ReleaseTrackEntity rt WHERE rt.pk.releaseId = :rid", String.class)
        .setParameter("rid", releaseId)
        .getResultList();
  }
```
Add imports for `SplitInvite`, `InviteOutcome`, `SplitInviteEntity` if not present.

- [ ] **Step 6: Mirror the 7 methods in `FakeCatalogRepository`**

The fake stores `Map<String, List<SplitEntry>> splitsByTrack` and `Map<String, Release> releases`. Add an invites map and implement against in-memory state:
```java
  private final Map<String, SplitInvite> invitesByHash = new HashMap<>();

  @Override
  public void saveSplitInvite(SplitInvite invite) { invitesByHash.put(invite.tokenHash(), invite); }

  @Override
  public Optional<SplitInvite> findSplitInviteByHash(String tokenHash) {
    return Optional.ofNullable(invitesByHash.get(tokenHash));
  }

  @Override
  public void consumeSplitInvite(String tokenHash, InviteOutcome outcome, Instant at) {
    SplitInvite i = invitesByHash.get(tokenHash);
    if (i != null) i.consume(outcome, at);
  }

  @Override
  public List<String> pendingSplitEmailsForRelease(ReleaseId releaseId) {
    return releaseTrackIds(releaseId).stream()
        .flatMap(tid -> splitsByTrack.getOrDefault(tid, List.of()).stream())
        .filter(s -> s.confirmation() == SplitConfirmation.pending)
        .map(SplitEntry::email).distinct().toList();
  }

  @Override
  public void confirmSplitsForReleaseEmail(ReleaseId releaseId, String email, String accountId) {
    remapReleaseEmailSplits(releaseId, email, s ->
        new SplitEntry(s.id(), s.trackId(), s.name(), s.email(), s.role(), s.percent(),
            SplitConfirmation.confirmed, accountId));
  }

  @Override
  public void declineSplitsForReleaseEmail(ReleaseId releaseId, String email) {
    remapReleaseEmailSplits(releaseId, email, s ->
        new SplitEntry(s.id(), s.trackId(), s.name(), s.email(), s.role(), s.percent(),
            SplitConfirmation.declined, s.accountId()));
  }

  @Override
  public void deleteUnconsumedInvitesForReleaseEmail(ReleaseId releaseId, String email) {
    invitesByHash.values().removeIf(i ->
        i.releaseId().equals(releaseId.value()) && i.email().equals(email) && !i.isConsumed());
  }

  private List<String> releaseTrackIds(ReleaseId releaseId) {
    Release r = releases.get(releaseId.value());
    return r == null ? List.of()
        : r.getTracks().stream().map(t -> t.trackId()).toList();
  }

  private void remapReleaseEmailSplits(ReleaseId releaseId, String email,
      java.util.function.UnaryOperator<SplitEntry> f) {
    for (String tid : releaseTrackIds(releaseId)) {
      List<SplitEntry> cur = splitsByTrack.get(tid);
      if (cur == null) continue;
      splitsByTrack.put(tid, cur.stream()
          .map(s -> s.email().equals(email) && s.confirmation() == SplitConfirmation.pending ? f.apply(s) : s)
          .toList());
    }
  }
```
Add imports `SplitInvite`, `InviteOutcome`, `SplitConfirmation`, `Optional`, `Instant` as needed. (Confirm `ReleaseTrack`'s accessor is `trackId()`; adjust if it differs.)

- [ ] **Step 7: Write the failing IT** — `SplitInvitePersistenceIT.java`

Model it on the existing `SplitPersistenceIT` (WU-CAT-6) for setup (seed a release + track + a pending split via `saveTrackSplits`). Assert:
```java
// (a) saveSplitInvite → findSplitInviteByHash round-trips all fields.
// (b) confirmSplitsForReleaseEmail flips only the matching email's pending rows to confirmed + sets accountId;
//     a different email's pending row is untouched; hasPendingSplits reflects the change.
// (c) declineSplitsForReleaseEmail flips to declined and does NOT set accountId.
// (d) consumeSplitInvite sets consumed_at + outcome (re-find shows isConsumed()==true).
// (e) deleteUnconsumedInvitesForReleaseEmail removes a pending invite but leaves a consumed one.
// (f) pendingSplitEmailsForRelease returns distinct pending emails only.
```
Use `@QuarkusTest` + inject `CatalogRepository` (the real Jpa impl), wrapping mutating calls in a `@Transactional` test method or `QuarkusTransaction.requiringNew().run(...)` per the existing catalog IT convention.

- [ ] **Step 8: Run the IT**

Run: `cd backend && ./mvnw -q test -Dtest=SplitInvitePersistenceIT` (Docker must be running for Testcontainers).
Expected: PASS. If Flyway complains the constraint name in the `DROP CONSTRAINT` doesn't exist, fix the migration to the actual name (Step 1 note).

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/resources/db/migration/V971__catalog_split_invite.sql \
        backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/out/persistence \
        backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/port/out/CatalogRepository.java \
        backend/src/test/java/org/shakvilla/beatzmedia/catalog/fakes/FakeCatalogRepository.java \
        backend/src/test/java/org/shakvilla/beatzmedia/catalog/it/SplitInvitePersistenceIT.java
git commit -m "feat(catalog): WU-CAT-9 split_invite table + accountId column + repo methods"
```

---

## Task 3: Application — invite issuance on submit + accept/decline/get/resend services

**Files:**
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/port/in/SplitInviteView.java`
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/port/in/GetSplitInvite.java`
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/port/in/AcceptSplitInvite.java`
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/port/in/DeclineSplitInvite.java`
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/port/in/ResendSplitInvites.java`
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/service/SplitInviteService.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/service/FinalizeReleaseService.java`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/catalog/application/SplitInviteServiceTest.java`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/catalog/application/FinalizeReleaseInvitesTest.java`

**Interfaces:**
- Consumes (Task 2): the 7 repo methods; `findArtist`, `tracksByIds`, `findRelease` (existing); `Clock`, `IdGenerator`, `AuditWriter`.
- Produces (Task 4):
  - `record SplitInviteView(String status, String artistName, String releaseTitle, List<TrackShareView> tracks)` + `record TrackShareView(String trackTitle, String role, int percent)`. `status ∈ "pending"|"accepted"|"declined"|"expired"`.
  - `interface GetSplitInvite { SplitInviteView getByToken(String token); }`
  - `interface AcceptSplitInvite { void accept(String token, String accountId); }`
  - `interface DeclineSplitInvite { void decline(String token); }`
  - `interface ResendSplitInvites { void resend(ReleaseId releaseId, ArtistId requestingArtist); }`
  - Domain event `SplitInviteIssued` is fired by `FinalizeReleaseService` and `SplitInviteService.resend`.

- [ ] **Step 1: Create the view + four input ports**

`SplitInviteView.java`:
```java
package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.List;

/** Read model for the collaborator accept page (WU-CAT-9). */
public record SplitInviteView(String status, String artistName, String releaseTitle,
    List<TrackShareView> tracks) {
  public record TrackShareView(String trackTitle, String role, int percent) {}
}
```
`GetSplitInvite.java`:
```java
package org.shakvilla.beatzmedia.catalog.application.port.in;

/** Input port: read a split invite by its opaque token (public accept page). WU-CAT-9. */
public interface GetSplitInvite {
  SplitInviteView getByToken(String token);
}
```
`AcceptSplitInvite.java`:
```java
package org.shakvilla.beatzmedia.catalog.application.port.in;

/** Input port: a logged-in collaborator accepts their split invite. WU-CAT-9. */
public interface AcceptSplitInvite {
  void accept(String token, String accountId);
}
```
`DeclineSplitInvite.java`:
```java
package org.shakvilla.beatzmedia.catalog.application.port.in;

/** Input port: a collaborator declines their split invite (no account required). WU-CAT-9. */
public interface DeclineSplitInvite {
  void decline(String token);
}
```
`ResendSplitInvites.java`:
```java
package org.shakvilla.beatzmedia.catalog.application.port.in;

import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;

/** Input port: the owning artist re-sends invites for all still-pending splits. WU-CAT-9. */
public interface ResendSplitInvites {
  void resend(ReleaseId releaseId, ArtistId requestingArtist);
}
```

- [ ] **Step 2: Write the failing service test** — `SplitInviteServiceTest.java`

Uses `FakeCatalogRepository`, a `FakeClock`, a `FakeIds`, a fake `AuditWriter`, and a captured CDI `Event<SplitInviteIssued>` (use a small test double implementing `jakarta.enterprise.event.Event` that records `fire(...)`, or Mockito). Cover:
```java
// accept_confirmsAllReleaseRowsForEmail_linksAccount_consumesInvite
//   seed release+2 tracks, pending splits for bob@x.com on both + alice@x.com on one;
//   save an invite for bob; accept(token,"acc-bob");
//   assert bob's two rows now confirmed + accountId="acc-bob"; alice untouched; invite consumed(accepted);
//   assert an AuditEntry was appended.
// accept_unknownToken_throwsSplitInviteNotFound
// accept_expiredToken_throwsSplitInviteGone  (advance FakeClock past expiry)
// accept_consumedToken_throwsSplitInviteGone
// decline_setsDeclined_noAccount_consumesInvite(declined)
// getByToken_returnsView_withStatusPending / accepted / expired
```
(Token hashing: the service hashes the incoming plaintext with SHA-256 and looks up by hash; in the test, store the invite under `sha256Hex("plain-token")` and call the port with `"plain-token"`.)

- [ ] **Step 3: Run it to verify failure**

Run: `cd backend && ./mvnw -q test -Dtest=SplitInviteServiceTest`
Expected: FAIL — `SplitInviteService` does not exist.

- [ ] **Step 4: Implement `SplitInviteService`**

```java
package org.shakvilla.beatzmedia.catalog.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.catalog.application.port.in.AcceptSplitInvite;
import org.shakvilla.beatzmedia.catalog.application.port.in.DeclineSplitInvite;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetSplitInvite;
import org.shakvilla.beatzmedia.catalog.application.port.in.ResendSplitInvites;
import org.shakvilla.beatzmedia.catalog.application.port.in.SplitInviteView;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistProfile;
import org.shakvilla.beatzmedia.catalog.domain.InviteOutcome;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException;
import org.shakvilla.beatzmedia.catalog.domain.SplitInvite;
import org.shakvilla.beatzmedia.catalog.domain.SplitInviteGoneException;
import org.shakvilla.beatzmedia.catalog.domain.SplitInviteIssued;
import org.shakvilla.beatzmedia.catalog.domain.SplitInviteNotFoundException;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.UnauthorizedException;

/**
 * WU-CAT-9 collaborator split invite/accept. Token model mirrors identity's password-reset:
 * only the SHA-256 hash is stored. Accept links the collaborator's account + confirms all their
 * pending splits on the release; decline releases the share to the creator; resend re-issues.
 * Every mutation appends an AuditEntry (INV-10).
 */
@ApplicationScoped
public class SplitInviteService
    implements GetSplitInvite, AcceptSplitInvite, DeclineSplitInvite, ResendSplitInvites {

  private final CatalogRepository repo;
  private final Clock clock;
  private final IdGenerator ids;
  private final AuditWriter auditWriter;
  private final Event<SplitInviteIssued> invites;
  private final long ttlSeconds;
  private final String acceptBaseUrl;

  @Inject
  public SplitInviteService(
      CatalogRepository repo, Clock clock, IdGenerator ids, AuditWriter auditWriter,
      Event<SplitInviteIssued> invites,
      @ConfigProperty(name = "beatz.catalog.split-invite-ttl-seconds", defaultValue = "1209600")
          long ttlSeconds,
      @ConfigProperty(name = "beatz.catalog.split-invite-accept-base-url",
          defaultValue = "http://localhost:5173/studio/splits/accept") String acceptBaseUrl) {
    this.repo = repo;
    this.clock = clock;
    this.ids = ids;
    this.auditWriter = auditWriter;
    this.invites = invites;
    this.ttlSeconds = ttlSeconds;
    this.acceptBaseUrl = acceptBaseUrl;
  }

  @Override
  public SplitInviteView getByToken(String token) {
    SplitInvite invite = repo.findSplitInviteByHash(sha256Hex(token))
        .orElseThrow(SplitInviteNotFoundException::new);
    Release release = repo.findRelease(new ReleaseId(invite.releaseId()))
        .orElseThrow(() -> new ReleaseNotFoundException(invite.releaseId()));
    String artistName = repo.findArtist(new ArtistId(release.getArtistId()))
        .map(ArtistProfile::getName).orElse("");
    List<SplitInviteView.TrackShareView> shares = collaboratorShares(release, invite.email());
    return new SplitInviteView(status(invite), artistName, release.getTitle(), shares);
  }

  @Override
  @Transactional
  public void accept(String token, String accountId) {
    SplitInvite invite = requireLive(token);
    Instant now = clock.now();
    repo.confirmSplitsForReleaseEmail(new ReleaseId(invite.releaseId()), invite.email(), accountId);
    repo.consumeSplitInvite(invite.tokenHash(), InviteOutcome.accepted, now);
    audit(accountId, "ACCEPT_SPLIT_INVITE", invite.releaseId(), now);
  }

  @Override
  @Transactional
  public void decline(String token) {
    SplitInvite invite = requireLive(token);
    Instant now = clock.now();
    repo.declineSplitsForReleaseEmail(new ReleaseId(invite.releaseId()), invite.email());
    repo.consumeSplitInvite(invite.tokenHash(), InviteOutcome.declined, now);
    audit(invite.email(), "DECLINE_SPLIT_INVITE", invite.releaseId(), now);
  }

  @Override
  @Transactional
  public void resend(ReleaseId releaseId, ArtistId requestingArtist) {
    Release release = repo.findRelease(releaseId)
        .orElseThrow(() -> new ReleaseNotFoundException(releaseId.value()));
    if (!release.getArtistId().equals(requestingArtist.value())) {
      throw new UnauthorizedException("Not your release");
    }
    issueInvitesForPending(release);
    audit(requestingArtist.value(), "RESEND_SPLIT_INVITES", releaseId.value(), clock.now());
  }

  /**
   * Mint one fresh invite per collaborator with pending splits on the release + fire the email
   * event. Shared by submit (WU-CAT-9 hook) and resend. Any prior unconsumed invite for that
   * collaborator is deleted first (resend supersedes). MUST run inside a transaction.
   */
  public void issueInvitesForPending(Release release) {
    ReleaseId releaseId = new ReleaseId(release.getId());
    List<String> emails = repo.pendingSplitEmailsForRelease(releaseId);
    if (emails.isEmpty()) return;
    String artistName = repo.findArtist(new ArtistId(release.getArtistId()))
        .map(ArtistProfile::getName).orElse("");
    Instant now = clock.now();
    for (String email : emails) {
      repo.deleteUnconsumedInvitesForReleaseEmail(releaseId, email);
      String plaintext = ids.newId() + ids.newId();
      SplitInvite invite = SplitInvite.issue(
          ids.newId(), release.getId(), email, sha256Hex(plaintext),
          now.plus(Duration.ofSeconds(ttlSeconds)), now);
      repo.saveSplitInvite(invite);
      String acceptUrl = acceptBaseUrl + "?token=" + plaintext;
      invites.fire(new SplitInviteIssued(
          email, acceptUrl, artistName, release.getTitle(), collaboratorShares(release, email).stream()
              .map(s -> new SplitInviteIssued.TrackShare(s.trackTitle(), s.role(), s.percent()))
              .toList()));
    }
  }

  private SplitInvite requireLive(String token) {
    SplitInvite invite = repo.findSplitInviteByHash(sha256Hex(token))
        .orElseThrow(SplitInviteNotFoundException::new);
    if (invite.isConsumed()) {
      throw new SplitInviteGoneException("Split invite already used");
    }
    if (invite.isExpired(clock.now())) {
      throw new SplitInviteGoneException("Split invite expired");
    }
    return invite;
  }

  private List<SplitInviteView.TrackShareView> collaboratorShares(Release release, String email) {
    List<String> trackIds = release.getTracks().stream().map(t -> t.trackId()).toList();
    List<Track> tracks = repo.tracksByIds(trackIds);
    // Re-derive this collaborator's per-track share from persisted splits via findRelease's splits.
    return release.getSplits().stream()
        .filter(s -> s.email().equals(email))
        .map(s -> new SplitInviteView.TrackShareView(
            tracks.stream().filter(t -> t.getId().equals(s.trackId())).map(Track::getTitle)
                .findFirst().orElse(""),
            s.role(), s.percent()))
        .toList();
  }

  private String status(SplitInvite invite) {
    if (invite.outcome() == InviteOutcome.accepted) return "accepted";
    if (invite.outcome() == InviteOutcome.declined) return "declined";
    if (invite.isExpired(clock.now())) return "expired";
    return "pending";
  }

  private void audit(String actor, String action, String releaseId, Instant now) {
    auditWriter.append(new AuditEntry(
        ids.newId(), actor, action, "Release", releaseId, AuditType.CATALOG, null, now));
  }

  private static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
```

**Note on `Release.getSplits()`:** WU-CAT-6 added `getSplits()` to `Release`, and `findRelease` populates it. `collaboratorShares` relies on it. Confirm `Release.getSplits()` exists (it does on this branch) and `Track` exposes `getId()`/`getTitle()`.

- [ ] **Step 5: Run the service test to verify pass**

Run: `cd backend && ./mvnw -q test -Dtest=SplitInviteServiceTest`
Expected: PASS.

- [ ] **Step 6: Hook invite issuance into `FinalizeReleaseService`**

Inject `SplitInviteService` and call it after the release is saved, inside the existing `@Transactional finalize(...)`. The idempotent-replay guard at the top already returns early, so a replay does **not** re-issue. In `finalize(...)`, after `repo.saveReleaseWithIdempotencyKey(release, idempotencyKey);` and before/after the SUBMIT_RELEASE audit, add:
```java
    // WU-CAT-9: issue collaborator split invites for any pending splits (fires SplitInviteIssued
    // per collaborator; notifications emails them). Re-fetch so getSplits() is populated.
    repo.findRelease(id).ifPresent(splitInviteService::issueInvitesForPending);
```
Add the constructor param + field:
```java
  private final SplitInviteService splitInviteService;
  // ...add SplitInviteService splitInviteService to the @Inject constructor + assign.
```

- [ ] **Step 7: Write the failing finalize test** — `FinalizeReleaseInvitesTest.java`

With `FakeCatalogRepository` seeded so a release has a pending split for `bob@x.com`, and a captured `Event<SplitInviteIssued>`: call `finalize(...)`; assert exactly one `SplitInviteIssued` was fired for `bob@x.com` with a non-blank `acceptUrl` containing `?token=`, and that an invite was persisted (`findSplitInviteByHash` non-empty for the hashed token — or assert `saveSplitInvite` was called via a fake accessor). Also assert a release with **no** pending splits fires zero events.

- [ ] **Step 8: Run + commit**

Run: `cd backend && ./mvnw -q test -Dtest=SplitInviteServiceTest,FinalizeReleaseInvitesTest`
Expected: PASS.
```bash
git add backend/src/main/java/org/shakvilla/beatzmedia/catalog/application \
        backend/src/test/java/org/shakvilla/beatzmedia/catalog/application/SplitInviteServiceTest.java \
        backend/src/test/java/org/shakvilla/beatzmedia/catalog/application/FinalizeReleaseInvitesTest.java
git commit -m "feat(catalog): WU-CAT-9 invite issuance on submit + accept/decline/resend services"
```

---

## Task 4: REST — public invite resource + artist resend endpoint

**Files:**
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/in/rest/SplitInviteResource.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/in/rest/StudioReleaseResource.java`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/catalog/it/SplitInviteResourceIT.java`

**Interfaces:**
- Consumes (Task 3): `GetSplitInvite`, `AcceptSplitInvite`, `DeclineSplitInvite`, `ResendSplitInvites`, `SplitInviteView`.

- [ ] **Step 1: Create `SplitInviteResource`**

```java
package org.shakvilla.beatzmedia.catalog.adapter.in.rest;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;

import io.quarkus.security.Authenticated;

import org.shakvilla.beatzmedia.catalog.application.port.in.AcceptSplitInvite;
import org.shakvilla.beatzmedia.catalog.application.port.in.DeclineSplitInvite;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetSplitInvite;
import org.shakvilla.beatzmedia.catalog.application.port.in.SplitInviteView;

/**
 * Public collaborator split invite surface (WU-CAT-9). GET + decline are {@code @PermitAll} (a
 * collaborator may not have an account); accept requires authentication so the split can link to a
 * real account. Thin: token in → port → DTO/204 out. Domain exceptions map to 404/410 via
 * DomainExceptionMapper.
 */
@Path("/v1/splits/invites")
@Produces(MediaType.APPLICATION_JSON)
public class SplitInviteResource {

  private final GetSplitInvite getSplitInvite;
  private final AcceptSplitInvite acceptSplitInvite;
  private final DeclineSplitInvite declineSplitInvite;
  private final JsonWebToken jwt;

  @Inject
  public SplitInviteResource(GetSplitInvite getSplitInvite, AcceptSplitInvite acceptSplitInvite,
      DeclineSplitInvite declineSplitInvite, JsonWebToken jwt) {
    this.getSplitInvite = getSplitInvite;
    this.acceptSplitInvite = acceptSplitInvite;
    this.declineSplitInvite = declineSplitInvite;
    this.jwt = jwt;
  }

  @GET
  @Path("/{token}")
  @PermitAll
  public SplitInviteView get(@PathParam("token") String token) {
    return getSplitInvite.getByToken(token);
  }

  @POST
  @Path("/{token}/accept")
  @Authenticated
  public Response accept(@PathParam("token") String token) {
    acceptSplitInvite.accept(token, jwt.getSubject());
    return Response.noContent().build();
  }

  @POST
  @Path("/{token}/decline")
  @PermitAll
  public Response decline(@PathParam("token") String token) {
    declineSplitInvite.decline(token);
    return Response.noContent().build();
  }
}
```

- [ ] **Step 2: Add `resend-invites` to `StudioReleaseResource`**

Inject `ResendSplitInvites resendSplitInvites` (add to the constructor + field). Add the method (class is already `@RolesAllowed("artist")` + has `artistId()` from the JWT):
```java
  @POST
  @Path("/{id}/resend-invites")
  public Response resendInvites(@PathParam("id") String id) {
    resendSplitInvites.resend(new ReleaseId(id), artistId());
    return Response.noContent().build();
  }
```
(Confirm `ReleaseId` is imported; `artistId()` returns `ArtistId`.)

- [ ] **Step 3: Write the failing IT** — `SplitInviteResourceIT.java`

`@QuarkusTest` using RestAssured + the test JWT helper the other studio ITs use. Seed via the real REST flow or SQL (mirror `StudioReleaseResourceIT`): create a draft, attach a track, PATCH a pending split for `bob@x.com`, submit (which issues the invite). Then read the persisted `token_hash`? — the plaintext token is only in the fired event/email, not returned by the API. For the IT, **capture the plaintext** by asserting through the DB is impossible (only the hash is stored). Instead: expose the token to the test by having `SplitInviteEmailObserver` (Task 5) not yet present — so in this REST IT, obtain the token by directly minting an invite through the injected `CatalogRepository` + a known plaintext, OR assert the endpoints against a hand-inserted invite row. Concretely:
```
// (a) Insert a SplitInvite via injected CatalogRepository with tokenHash = sha256Hex("tok-1"),
//     linked to a seeded release with a pending split for bob@x.com.
// (b) GET /v1/splits/invites/tok-1 (no auth) → 200, status "pending", artistName/releaseTitle/tracks present.
// (c) GET /v1/splits/invites/nope → 404 SPLIT_INVITE_NOT_FOUND.
// (d) POST /v1/splits/invites/tok-1/accept WITHOUT a JWT → 401.
// (e) POST /v1/splits/invites/tok-1/accept WITH bob's JWT (sub=acc-bob) → 204;
//     assert bob's split now confirmed + account_id=acc-bob (query via repo); invite consumed.
// (f) POST /v1/splits/invites/tok-1/accept again → 410 SPLIT_INVITE_GONE.
// (g) POST /v1/studio/releases/{id}/resend-invites with the owning artist JWT → 204 (idempotent smoke).
```

- [ ] **Step 4: Run + commit**

Run: `cd backend && ./mvnw -q test -Dtest=SplitInviteResourceIT`
Expected: PASS.
```bash
git add backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/in/rest \
        backend/src/test/java/org/shakvilla/beatzmedia/catalog/it/SplitInviteResourceIT.java
git commit -m "feat(catalog): WU-CAT-9 SplitInviteResource + studio resend-invites endpoint"
```

---

## Task 5: Notifications — email the invite to the raw address

**Files:**
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/notifications/adapter/in/events/SplitInviteEmailObserver.java`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/notifications/it/SplitInviteEmailIT.java`

**Interfaces:**
- Consumes: `org.shakvilla.beatzmedia.catalog.domain.SplitInviteIssued` (reacting to another module's domain event is allowed); `notifications.application.port.out.Mailer` + `EmailMessage`.

- [ ] **Step 1: Create the observer** (mirrors `NotificationEventObservers.onTipReceived`: `AFTER_SUCCESS` + `REQUIRES_NEW`)

```java
package org.shakvilla.beatzmedia.notifications.adapter.in.events;

import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.catalog.domain.SplitInviteIssued;
import org.shakvilla.beatzmedia.notifications.application.port.out.EmailMessage;
import org.shakvilla.beatzmedia.notifications.application.port.out.Mailer;

/**
 * WU-CAT-9: emails a collaborator their split invite when catalog fires {@link SplitInviteIssued}
 * on release submit. Sends to the raw invited address (a collaborator may not be a BeatzClik user),
 * so it bypasses the AccountId-gated in-app notification path and calls {@link Mailer} directly.
 * Reacts only to the event payload — no catalog table reads (hexagonal rule). AFTER_SUCCESS so the
 * email is sent only once the submit transaction has durably committed; REQUIRES_NEW because no tx
 * is active on the thread during AFTER_SUCCESS.
 */
@ApplicationScoped
public class SplitInviteEmailObserver {

  private final Mailer mailer;

  @Inject
  public SplitInviteEmailObserver(Mailer mailer) {
    this.mailer = mailer;
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void onSplitInviteIssued(
      @Observes(during = TransactionPhase.AFTER_SUCCESS) SplitInviteIssued event) {
    String shares = event.trackShares().stream()
        .map(s -> "  • " + s.trackTitle() + " — " + s.role() + " — " + s.percent() + "%")
        .collect(Collectors.joining("\n"));
    String body = event.artistName() + " wants to split earnings with you on \""
        + event.releaseTitle() + "\":\n\n" + shares
        + "\n\nReview and accept or decline your split:\n" + event.acceptUrl()
        + "\n\nIf you don't recognise this, you can ignore this email.";
    // idempotencyKey: the accept URL is unique per issued invite (unique token), so it dedupes resends.
    mailer.send(new EmailMessage(
        event.email(), "You've been added to a release on BeatzClik", body, event.acceptUrl()));
  }
}
```

- [ ] **Step 2: Write the failing IT** — `SplitInviteEmailIT.java`

`@QuarkusTest`. Fire a `SplitInviteIssued` (inject `Event<SplitInviteIssued>` and `fire(...)` inside a `@Transactional` test method so `AFTER_SUCCESS` triggers on commit — mirror how other AFTER_SUCCESS observers are tested, e.g. the tips/notification ITs). Assert an email landed in Mailpit for `bob@x.com` whose body contains the accept URL. If the notifications test suite instead uses a captured/fake `Mailer` for unit-level assertions, follow that convention: assert `Mailer.send` was invoked with `to == "bob@x.com"` and a body containing the URL. (Check `backend/docs/sdlc/testing-strategy.md` + an existing notifications IT for the Mailpit assertion helper.)

- [ ] **Step 3: Run + commit**

Run: `cd backend && ./mvnw -q test -Dtest=SplitInviteEmailIT`
Expected: PASS.
```bash
git add backend/src/main/java/org/shakvilla/beatzmedia/notifications/adapter/in/events/SplitInviteEmailObserver.java \
        backend/src/test/java/org/shakvilla/beatzmedia/notifications/it/SplitInviteEmailIT.java
git commit -m "feat(notifications): WU-CAT-9 email collaborator split invites on SplitInviteIssued"
```

---

## Task 6: Docs, contract, backlog + verification gate + PR

**Files:**
- Modify: `backend/docs/architecture/catalog.md`
- Modify: `backend/docs/00-system-architecture.md` (new ADR)
- Modify: `API-CONTRACT.md`
- Modify: `backend/.project/backlog.yaml`

- [ ] **Step 1: Catalog ADD (`catalog.md`)** — add an as-built section "Implementation notes (WU-CAT-9, as-built)" describing: `split_invite` token table (hash-only, single-use, 14-day TTL); `SplitEntry.accountId` (no cross-module FK) + `declined` state; invites issued on submit via `SplitInviteIssued`, emailed by notifications to the raw address; accept links `accountId` + confirms all the collaborator's release rows and clears the go-live block; decline releases the share to the creator; `resend-invites` re-issues. Update the §9 pending-note that WU-CAT-6 left ("the invite/accept flow ... is a separate follow-on WU") to point at WU-CAT-9 as delivered.

- [ ] **Step 2: ADR** in `backend/docs/00-system-architecture.md` §9 (one-row table entry matching existing ADR format): "Collaborator split invites use an opaque single-use token (SHA-256-hashed, 14-day TTL) verified by hash, mirroring password reset; accept is account-linked (`@Authenticated`, stamps `jwt.subject`), decline is `@PermitAll`. **Residual risks (accepted):** (a) token possession authorizes acceptance — the logged-in account's email is not required to match the invited email, bounded by TTL + single-use; (b) routing a confirmed collaborator's earnings into their payout balance is deferred to a payments/royalty WU (OQ-4)."

- [ ] **Step 3: API-CONTRACT.md** — document additively:
  - `GET /v1/splits/invites/{token}` → `SplitInviteView { status: "pending"|"accepted"|"declined"|"expired", artistName, releaseTitle, tracks: [{trackTitle, role, percent}] }`; 404 `SPLIT_INVITE_NOT_FOUND`.
  - `POST /v1/splits/invites/{token}/accept` → 204 (auth required; 401 if unauthenticated; 410 `SPLIT_INVITE_GONE` if consumed/expired).
  - `POST /v1/splits/invites/{token}/decline` → 204 (public).
  - `POST /v1/studio/releases/{id}/resend-invites` → 204 (artist; 403/404 if not owner).

- [ ] **Step 4: Register WU-CAT-9 in `backlog.yaml`**

Add after the WU-CAT-8 entry (keep the existing WU-CAT-6 entry intact):
```yaml
  - id: WU-CAT-9
    title: Collaborator split invite/accept flow
    phase: 1
    module: catalog
    add: catalog.md
    owner: backend-engineer
    depends_on: [WU-CAT-6]
    llfrs: [LLFR-CATALOG-02.2]
    status: in_progress
```

- [ ] **Step 5: Commit docs**

```bash
git add backend/docs/architecture/catalog.md backend/docs/00-system-architecture.md \
        API-CONTRACT.md backend/.project/backlog.yaml
git commit -m "docs(catalog): WU-CAT-9 as-built + ADR + API-CONTRACT + backlog"
```

- [ ] **Step 6: USER runs the full gate**

Tell the USER to run (do not run it yourself):
```
git checkout feat/WU-CAT-9-collaborator-invite-accept
bash backend/scripts/verify.sh && bash backend/scripts/smoke.sh
```
Expected: `Local gate PASSED` + `Smoke PASSED`. If Spotless flags import order in any new file (the common nit), fix per its diff and re-run. Paste the result back.

- [ ] **Step 7: Open the PR** (after the gate is green, and only after ensuring `application.properties` / `docker-compose.yml` are NOT staged)

```bash
git push -u origin feat/WU-CAT-9-collaborator-invite-accept
# gh pr create --base master ... (stacked on WU-CAT-6 #152; note in the PR body it depends on #152 merging first)
```
The PR body must note: **stacked on WU-CAT-6 (#152)** — this branch contains #152's commits, so it should merge after #152, or be rebased onto master once #152 lands.

---

## Self-Review notes (author)

- **Spec coverage:** §1 data model → Tasks 1–2; §2 state machine/guard → Tasks 1–3 (guard unchanged, verified in IT); §3 endpoints → Task 4; §3.3 view → Task 3; §4 trigger+email → Tasks 3 & 5; §5 files → all; §6 testing → per-task + Task 6 gate; §7 DoD → Task 6; §8 out-of-scope respected (no frontend, no earnings routing, no identity changes).
- **Type consistency:** `SplitEntry` 8-arg canonical + 7-arg convenience used consistently (load maps 8-arg, `UpdateReleaseService` keeps 7-arg); repo method names identical across port/JPA/fake/tests; `SplitInviteView`/`TrackShareView` and `SplitInviteIssued`/`TrackShare` names stable; `ErrorCode.SPLIT_INVITE_NOT_FOUND`/`SPLIT_INVITE_GONE` referenced identically in exceptions + mapper.
- **Dependency:** branch is stacked on WU-CAT-6 (unmerged #152) because it consumes `saveTrackSplits`, `TrackRef.splits`, `Release.getSplits`, and the pending-split persistence — none of which are on master yet.
