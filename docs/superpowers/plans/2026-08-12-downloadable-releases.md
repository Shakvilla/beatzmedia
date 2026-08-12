# Downloadable Releases Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An artist decides, per release, whether buyers may download the audio; when allowed, an owner can download a lossless FLAC file.

**Architecture:** Three additive columns (`release.downloadable`, `ownership_grant.downloadable`, `media_asset.lossless_key`). The media pipeline gains a third delivery rendition. A new `GET /v1/tracks/{id}/download` in the **playback** module mirrors `GET /v1/tracks/{id}/stream` exactly — same resource, same service shape, same cross-module-via-input-port rule. The permission is captured onto the ownership grant at settlement and read from the **grant**, never from the release.

**Tech Stack:** Java 25, Quarkus 3.36.x, PostgreSQL 16 + Flyway, ffmpeg (FLAC), JUnit 5 + Testcontainers, React 19 + TanStack Router, Vitest.

## Global Constraints

- **Hexagonal rule (ArchUnit-enforced):** `adapter → application → domain`. Domain imports no framework. Modules never read another module's tables — call its input port.
- **Never stage these files:** `backend/src/main/resources/application.properties`, `backend/docker-compose.yml`, `Frontend/vite.config.ts`.
- **Migrations:** forward-only. Allocate every version with `bash backend/scripts/next-migration-version.sh` at the moment you write it. Never hardcode a version from this plan — other work may land first.
- **The grant is the authority.** The download endpoint reads `ownership_grant.downloadable`. Reading `release.downloadable` there would retract downloads from people who already paid. This is the single most likely mistake in this plan.
- **Frontend Node:** 22.17.1 (`Frontend/.nvmrc`). Prefix commands with `export PATH="$HOME/.nvm/versions/node/v22.17.1/bin:$PATH"`.
- **Frontend gate:** `npm run build` (runs `tsc -b`) **and** `npx vitest run`. Vitest does not typecheck; the build does. Both must pass.
- **Backend gate:** `bash backend/scripts/verify.sh && bash backend/scripts/smoke.sh` are **run by the user, not by you.** Ask; do not run them.
- **Spotless:** `cd backend && ./mvnw -o spotless:apply` before every backend commit.
- **Commits:** Conventional Commits. Use scope `download`, e.g. `feat(download): ...`.

---

## File Structure

**Backend — new files**

| File | Responsibility |
|---|---|
| `media/domain/DeliveryVariant.java` *(modify)* | Add `LOSSLESS` |
| `media/domain/MediaAsset.java` *(modify)* | Hold `losslessKey`; resolve it in `resolveDeliveryKey` |
| `media/application/port/out/AudioTranscoderPort.java` *(modify)* | Add `transcodeLossless` |
| `media/adapter/out/integration/FfmpegAudioTranscoderAdapter.java` *(modify)* | Implement FLAC transcode |
| `catalog/domain/Release.java` *(modify)* | Hold `downloadable`; publish guard |
| `commerce/domain/OwnershipGrant.java` *(modify)* | Hold `downloadable`, captured at construction |
| `playback/application/port/in/GetDownloadUrl.java` | Input port |
| `playback/application/port/in/DownloadUrlResult.java` | `{ url, expiresAt, format, sizeBytes }` |
| `playback/application/port/out/DownloadPermissionReader.java` | Output port: may this account download this track? |
| `playback/application/service/GetDownloadUrlService.java` | The four guards, in order |
| `playback/adapter/out/integration/DownloadPermissionReaderAdapter.java` | Calls commerce's input port |
| `playback/domain/DownloadNotAllowedException.java` | `409 DOWNLOAD_NOT_ALLOWED` |
| `playback/domain/DownloadNotReadyException.java` | `409 DOWNLOAD_NOT_READY` |

**Frontend — modified**

`studio.release.new.details.tsx` (required choice) · `studio.release.$releaseId.tsx` (editable) · `track.$trackId.tsx`, `album/$albumId.tsx` (marker) · `store.hifi.tsx` (copy) · `library.tsx` (download action) · `settings.tsx` (remove dead selector) · `lib/api/mappers.ts`, `lib/api/queries/*` (wire types)

---

## Task 1: Media — the LOSSLESS delivery variant

**Files:**
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/media/domain/DeliveryVariant.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/media/domain/MediaAsset.java`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/media/domain/MediaAssetLosslessTest.java`

**Interfaces:**
- Produces: `DeliveryVariant.LOSSLESS`; `MediaAsset.markLosslessReady(ObjectKey)`; `MediaAsset.getLosslessKey()`; `resolveDeliveryKey(LOSSLESS)` returns the lossless key or throws.

- [ ] **Step 1: Write the failing test**

```java
package org.shakvilla.beatzmedia.media.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * LOSSLESS is a third rendition beside FULL and PREVIEW. It must never fall back to FULL: a
 * download that silently hands over 128k AAC on a platform selling lossless masters is exactly the
 * "claims what it did not do" failure this feature exists to avoid.
 */
@Tag("unit")
class MediaAssetLosslessTest {

  private static final Instant NOW = Instant.parse("2026-08-12T00:00:00Z");

  private static MediaAsset readyAsset() {
    MediaAsset a = MediaAsset.uploading(
        new MediaAssetId("m1"), new OwnerRef("track:t1"), MediaKind.AUDIO,
        new ObjectKey("originals", "o/m1.wav"), NOW, "hash");
    a.markReady(new ObjectKey("delivery", "d/m1/full.m4a"),
        new ObjectKey("delivery", "d/m1/preview.m4a"), 180);
    return a;
  }

  @Test
  void losslessKeyIsResolvedOnceSet() {
    MediaAsset a = readyAsset();
    a.markLosslessReady(new ObjectKey("delivery", "d/m1/lossless.flac"));

    assertEquals("d/m1/lossless.flac",
        a.resolveDeliveryKey(DeliveryVariant.LOSSLESS).key());
  }

  @Test
  void anAssetWithNoLosslessRenditionThrowsRatherThanFallingBackToFull() {
    MediaAsset a = readyAsset();

    assertThrows(IllegalStateException.class,
        () -> a.resolveDeliveryKey(DeliveryVariant.LOSSLESS));
  }

  @Test
  void addingLosslessDoesNotDisturbFullOrPreview() {
    MediaAsset a = readyAsset();
    a.markLosslessReady(new ObjectKey("delivery", "d/m1/lossless.flac"));

    assertEquals("d/m1/full.m4a", a.resolveDeliveryKey(DeliveryVariant.FULL).key());
    assertEquals("d/m1/preview.m4a", a.resolveDeliveryKey(DeliveryVariant.PREVIEW).key());
  }
}
```

- [ ] **Step 2: Run it and confirm it fails**

```bash
cd backend && ./mvnw -o test -Dtest=MediaAssetLosslessTest
```

Expected: compilation failure — `LOSSLESS`, `markLosslessReady` do not exist.

- [ ] **Step 3: Add the enum constant**

In `DeliveryVariant.java`, add `LOSSLESS` and extend the javadoc:

```java
public enum DeliveryVariant {
  FULL,
  PREVIEW,
  /**
   * The {@code lossless.flac} rendition — the download payload, owners only, and only when the
   * grant permits it. Separate from FULL because FULL is AAC 128k: serving it as "the download"
   * would hand over a lossy file on a platform selling lossless masters.
   */
  LOSSLESS
}
```

- [ ] **Step 4: Add the field, mutator and resolution branch**

In `MediaAsset.java`, add `private ObjectKey losslessKey;` beside `previewKey`, then:

```java
  /**
   * Attach the lossless rendition. Separate from {@link #markReady} on purpose: READY means
   * playable, and playback must not wait on a FLAC transcode that only downloads need.
   */
  public void markLosslessReady(ObjectKey losslessKey) {
    if (losslessKey == null) {
      throw new IllegalArgumentException("losslessKey must not be null");
    }
    this.losslessKey = losslessKey;
  }

  public ObjectKey getLosslessKey() {
    return losslessKey;
  }
```

In `resolveDeliveryKey`, add this branch **before** the PREVIEW fallthrough:

```java
    if (variant == DeliveryVariant.LOSSLESS) {
      if (losslessKey == null) {
        // Deliberately not a fallback to fullKey — see DeliveryVariant.LOSSLESS.
        throw new IllegalStateException("losslessKey is null for asset " + id.value());
      }
      return losslessKey;
    }
```

- [ ] **Step 5: Run the test and confirm it passes**

```bash
cd backend && ./mvnw -o test -Dtest=MediaAssetLosslessTest
```

Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
cd backend && ./mvnw -o spotless:apply
git add backend/src/main/java/org/shakvilla/beatzmedia/media/domain backend/src/test/java/org/shakvilla/beatzmedia/media/domain/MediaAssetLosslessTest.java
git commit -m "feat(download): add the LOSSLESS delivery variant to MediaAsset"
```

---

## Task 2: Media — persist `lossless_key`

**Files:**
- Create: `backend/src/main/resources/db/migration/V<next>__media_asset_lossless_key.sql`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/media/adapter/out/persistence/MediaAssetEntity.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/media/adapter/out/persistence/MediaAssetMapper.java`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/media/adapter/out/persistence/MediaAssetMapperTest.java` *(exists — extend)*

**Interfaces:**
- Consumes: `MediaAsset.markLosslessReady`, `getLosslessKey()` (Task 1)
- Produces: `media_asset.lossless_key` round-trips through the mapper

- [ ] **Step 1: Allocate the version**

```bash
bash backend/scripts/next-migration-version.sh
```

Use the number it prints. Do not reuse a number written in this plan.

- [ ] **Step 2: Write the migration**

`V<next>__media_asset_lossless_key.sql`:

```sql
-- Downloadable releases: the FLAC rendition a download hands over.
--
-- Nullable because it is produced after READY — playback must not wait on a FLAC transcode that
-- only downloads need. An asset with no lossless_key answers 409 DOWNLOAD_NOT_READY rather than
-- silently serving the 128k AAC full rendition.
ALTER TABLE media_asset ADD COLUMN lossless_key varchar(255);
```

- [ ] **Step 3: Write the failing mapper test**

Append to `MediaAssetMapperTest.java`:

```java
  @Test
  void losslessKeyRoundTripsThroughTheMapper() {
    MediaAsset asset = MediaAsset.uploading(
        new MediaAssetId("m1"), new OwnerRef("track:t1"), MediaKind.AUDIO,
        new ObjectKey("originals", "o/m1.wav"), Instant.parse("2026-08-12T00:00:00Z"), "hash");
    asset.markReady(new ObjectKey("delivery", "d/m1/full.m4a"),
        new ObjectKey("delivery", "d/m1/preview.m4a"), 180);
    asset.markLosslessReady(new ObjectKey("delivery", "d/m1/lossless.flac"));

    MediaAsset back = MediaAssetMapper.toDomain(MediaAssetMapper.toEntity(asset));

    assertEquals("d/m1/lossless.flac", back.getLosslessKey().key());
  }
```

- [ ] **Step 4: Run it and confirm it fails**

```bash
cd backend && ./mvnw -o test -Dtest=MediaAssetMapperTest
```

Expected: FAIL — `getLosslessKey()` returns null because the mapper drops it.

- [ ] **Step 5: Add the column to the entity and both mapper directions**

In `MediaAssetEntity.java`:

```java
  @Column(name = "lossless_key")
  public String losslessKey;
```

In `MediaAssetMapper.toEntity`, set `e.losslessKey = asset.getLosslessKey() != null ? asset.getLosslessKey().key() : null;` using the same bucket/key convention the file already uses for `fullKey`. In `toDomain`, call `markLosslessReady(...)` when the column is non-null, mirroring how `fullKey`/`previewKey` are rehydrated.

- [ ] **Step 6: Run it and confirm it passes**

```bash
cd backend && ./mvnw -o test -Dtest=MediaAssetMapperTest
```

Expected: all green.

- [ ] **Step 7: Commit**

```bash
cd backend && ./mvnw -o spotless:apply
git add backend/src/main/resources/db/migration backend/src/main/java/org/shakvilla/beatzmedia/media/adapter/out/persistence backend/src/test/java/org/shakvilla/beatzmedia/media/adapter/out/persistence
git commit -m "feat(download): persist the lossless rendition key"
```

---

## Task 3: Media — transcode FLAC

**Files:**
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/media/application/port/out/AudioTranscoderPort.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/media/adapter/out/integration/FfmpegAudioTranscoderAdapter.java`
- Modify: `backend/src/test/java/org/shakvilla/beatzmedia/media/it/RealTranscodeIT.java`

**Interfaces:**
- Produces: `AudioTranscoderPort.transcodeLossless(ObjectKey original, MediaAssetId id) → ObjectKey` writing `delivery/{id}/lossless.flac`

- [ ] **Step 1: Add the port method**

```java
  /**
   * Transcode the original to the LOSSLESS delivery rendition: a single FLAC object at
   * {@code delivery/{id}/lossless.flac}. This is the download payload.
   *
   * <p>FLAC rather than the original upload: formats vary per track and sizes are unbounded, and
   * handing over the artist's master is the thing an artist disabling downloads is protecting.
   * FLAC over WAV for roughly half the size at identical fidelity.
   */
  ObjectKey transcodeLossless(ObjectKey original, MediaAssetId id);
```

- [ ] **Step 2: Extend `RealTranscodeIT` with a failing assertion**

Add to `RealTranscodeIT.transcode_wav_produces_full_and_a_genuinely_clipped_preview` a new sibling test:

```java
  /**
   * A lossless rendition that is silently clipped, or silently lossy, would pass a mere
   * "the object exists" check. Assert the duration matches the source — the same reasoning that
   * makes the preview assertion the real proof of INV-3.
   */
  @Test
  void transcode_produces_a_full_length_lossless_rendition() throws Exception {
    assumeTrue(ffmpegOnPath());

    MediaAssetId id = new MediaAssetId("lossless-1");
    ObjectKey originalKey = uploadSourceWav(id);

    ObjectKey losslessKey = transcoder.transcodeLossless(originalKey, id);

    assertNotNull(losslessKey, "lossless key must not be null");
    assertTrue(losslessKey.key().endsWith("/lossless.flac"),
        "lossless rendition must be a single .flac: " + losslessKey.key());
    assertTrue(objectStore.exists(losslessKey), "lossless rendition must exist in delivery bucket");
    assertDurationNear(SOURCE_SECONDS, transcoder.probeDurationSec(losslessKey), "lossless");
  }
```

If `uploadSourceWav(MediaAssetId)` does not already exist in the file, extract it from the existing test's setup so both tests share one source upload — do not duplicate the upload block.

- [ ] **Step 3: Run it and confirm it fails**

```bash
cd backend && ./mvnw -o verify -DskipITs=false -Dit.test=RealTranscodeIT
```

Expected: compilation failure — `transcodeLossless` is not implemented.

- [ ] **Step 4: Implement in the adapter**

In `FfmpegAudioTranscoderAdapter`, add `transcodeLossless` following the existing `transcodeFull` structure exactly (download original → run ffmpeg → upload result → delete temp). The ffmpeg invocation:

```java
    List<String> cmd = new ArrayList<>();
    cmd.add("ffmpeg");
    cmd.add("-nostdin");
    cmd.add("-y");
    cmd.add("-i"); cmd.add(inputFile.toAbsolutePath().toString());
    cmd.add("-c:a"); cmd.add("flac");
    cmd.add("-compression_level"); cmd.add("8");
    cmd.add(outputFile.toAbsolutePath().toString());
```

Write the object to `delivery/{id}/lossless.flac`.

- [ ] **Step 5: Run it and confirm it passes**

```bash
cd backend && ./mvnw -o verify -DskipITs=false -Dit.test=RealTranscodeIT
```

Expected: both tests green, `Skipped: 0`. If it reports `Tests run: 0`, ffmpeg is not on your PATH — the test skipped and proved nothing. Install it before continuing.

- [ ] **Step 6: Commit**

```bash
cd backend && ./mvnw -o spotless:apply
git add backend/src/main/java/org/shakvilla/beatzmedia backend/src/test/java/org/shakvilla/beatzmedia/media/it/RealTranscodeIT.java
git commit -m "feat(download): transcode a lossless FLAC delivery rendition"
```

---

## Task 4: Catalog — the artist's choice and its publish guard

**Files:**
- Create: `backend/src/main/resources/db/migration/V<next>__release_downloadable.sql`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/domain/Release.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/service/PublishReleaseService.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/out/persistence/ReleaseEntity.java`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/catalog/application/PublishReleaseDownloadChoiceTest.java`

**Interfaces:**
- Produces: `Release.getDownloadable() → Boolean` (nullable = unanswered); `PublishRelease` throws `ValidationException("DOWNLOAD_CHOICE_REQUIRED", "downloadable")` on null.

- [ ] **Step 1: Allocate the version and write the migration**

```bash
bash backend/scripts/next-migration-version.sh
```

`V<next>__release_downloadable.sql`:

```sql
-- Downloadable releases: may buyers download this release's audio?
--
-- Nullable and WITHOUT a default, deliberately. NULL means "the artist has not chosen yet", which
-- PublishRelease rejects. A default would mean inertia decides: off-by-default quietly makes the
-- platform stream-only, on-by-default means "the artist chooses" degrades to "the artist notices".
ALTER TABLE release ADD COLUMN downloadable boolean;
```

- [ ] **Step 2: Write the failing test**

```java
package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * The artist must choose whether buyers may download, and publish is the gate that enforces it.
 *
 * <p>Without a server-side guard, "required choice" is a UI convention any other client can skip —
 * and the choice would then be decided by whatever the column defaults to, which is precisely what
 * the design rejected.
 */
@Tag("unit")
class PublishReleaseDownloadChoiceTest {

  @Test
  void publishingWithNoDownloadChoiceIsRejected() {
    // Build a release that is otherwise publishable but has downloadable == null.
    ValidationException e = assertThrows(
        ValidationException.class,
        () -> publishReleaseService.publish(releaseWithDownloadable(null), ARTIST));

    assertEquals("downloadable", e.getField());
  }

  @Test
  void publishingWithDownloadsAllowedSucceeds() {
    assertDoesNotThrow(() -> publishReleaseService.publish(releaseWithDownloadable(true), ARTIST));
  }

  @Test
  void publishingWithDownloadsRefusedAlsoSucceeds() {
    // "No" is a complete answer, not an absent one.
    assertDoesNotThrow(() -> publishReleaseService.publish(releaseWithDownloadable(false), ARTIST));
  }
}
```

Wire the fixture (`publishReleaseService`, `releaseWithDownloadable`, `ARTIST`) using the fakes the existing catalog service tests already use — follow whichever `*ServiceTest` in `catalog/application/` constructs `PublishReleaseService`, and reuse its fakes rather than writing new ones.

- [ ] **Step 3: Run it and confirm it fails**

```bash
cd backend && ./mvnw -o test -Dtest=PublishReleaseDownloadChoiceTest
```

Expected: FAIL — no guard exists, so the null case does not throw.

- [ ] **Step 4: Add the field and the guard**

In `Release.java`, add `private Boolean downloadable;` with a getter and a setter used by the draft-update path.

In `PublishReleaseService.publish(...)`, before any state transition:

```java
    // The artist's choice is required, not defaulted — see V<n>__release_downloadable.sql.
    if (release.getDownloadable() == null) {
      throw new ValidationException(
          "Choose whether buyers may download this release before publishing.", "downloadable");
    }
```

In `ReleaseEntity.java`, add:

```java
  @Column(name = "downloadable")
  public Boolean downloadable;
```

and map it in both directions wherever `ReleaseEntity` is converted.

- [ ] **Step 5: Run it and confirm it passes**

```bash
cd backend && ./mvnw -o test -Dtest=PublishReleaseDownloadChoiceTest
```

Expected: `Tests run: 3, Failures: 0`

- [ ] **Step 6: Commit**

```bash
cd backend && ./mvnw -o spotless:apply
git add backend/src/main/resources/db/migration backend/src/main/java/org/shakvilla/beatzmedia/catalog backend/src/test/java/org/shakvilla/beatzmedia/catalog
git commit -m "feat(download): require an explicit download choice before publish"
```

---

## Task 4b: Studio write path — let the artist actually set the choice

**Added during execution.** Task 4's guard makes `downloadable` required at publish, but no REST
path can set it: `CreateDraftBody` and `UpdateReleaseBody` in
`catalog/adapter/in/rest/StudioReleaseResource.java` carry `title/genre/description/visibility/
scheduledAt/tracks` and nothing else. Without this task the guard rejects **every** publish through
the real API, and Task 8's wizard control has nowhere to persist to. This was a gap in the original
plan, not a deviation by any implementer.

**Files:**
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/in/rest/StudioReleaseResource.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/port/in/UpdateRelease.java` (the `UpdateReleaseCommand` record)
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/service/UpdateReleaseService.java`
- Modify: the create-draft command/service equivalents
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/catalog/application/UpdateReleaseServiceTest.java`

**Interfaces:**
- Consumes: `Release.getDownloadable()` / setter (Task 4)
- Produces: `PATCH /v1/studio/releases/{id}` and `POST /v1/studio/releases` accept
  `downloadable: boolean | null`; a subsequent publish of that release succeeds.

- [ ] **Step 1: Write the failing test**

Add to `UpdateReleaseServiceTest`:

```java
  @Test
  void updateSetsTheDownloadChoice() {
    service.update(RELEASE_ID, ARTIST, commandWithDownloadable(true));
    assertEquals(Boolean.TRUE, repository.find(RELEASE_ID).orElseThrow().getDownloadable());
  }

  @Test
  void updateCanSetTheChoiceToFalse() {
    // `false` is a complete answer, not an absent one — it must persist, not be treated as unset.
    service.update(RELEASE_ID, ARTIST, commandWithDownloadable(false));
    assertEquals(Boolean.FALSE, repository.find(RELEASE_ID).orElseThrow().getDownloadable());
  }

  @Test
  void omittingTheChoiceLeavesThePreviousValueAlone() {
    // PATCH is partial: a body that does not mention downloadable must not silently clear a choice
    // the artist already made, which would make the release unpublishable again.
    service.update(RELEASE_ID, ARTIST, commandWithDownloadable(true));
    service.update(RELEASE_ID, ARTIST, commandWithDownloadable(null));
    assertEquals(Boolean.TRUE, repository.find(RELEASE_ID).orElseThrow().getDownloadable());
  }
```

Wire `commandWithDownloadable` from the existing test's command builder.

- [ ] **Step 2: Run it and confirm it fails**

```bash
cd backend && ./mvnw -o test -Dtest=UpdateReleaseServiceTest
```

- [ ] **Step 3: Thread `downloadable` through**

Add `Boolean downloadable` to `CreateDraftBody`, `UpdateReleaseBody`, and `UpdateReleaseCommand`
(and the create-draft command). In `UpdateReleaseService`, apply it **only when non-null** — see the
third test: PATCH is partial, and clearing a made choice would make the release unpublishable.

- [ ] **Step 4: Run it and confirm it passes**

```bash
cd backend && ./mvnw -o test -Dtest=UpdateReleaseServiceTest
```

- [ ] **Step 5: Commit**

```bash
cd backend && ./mvnw -o spotless:apply
git add backend/src/main/java/org/shakvilla/beatzmedia/catalog backend/src/test/java/org/shakvilla/beatzmedia/catalog
git commit -m "feat(download): accept the download choice on the studio release write path"
```


---

## Task 5: Commerce — capture the permission on the grant

**Files:**
- Create: `backend/src/main/resources/db/migration/V<next>__ownership_grant_downloadable.sql`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/commerce/domain/OwnershipGrant.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/commerce/application/service/GrantOwnershipService.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/commerce/adapter/out/persistence/OwnershipGrantEntity.java`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/commerce/application/GrantDownloadPermissionTest.java`

**Interfaces:**
- Consumes: `Release.getDownloadable()` (Task 4)
- Produces: `OwnershipGrant.forTrack(..., boolean downloadable)`; `OwnershipGrant.isDownloadable()`

- [ ] **Step 1: Allocate the version and write the migration**

```bash
bash backend/scripts/next-migration-version.sh
```

`V<next>__ownership_grant_downloadable.sql`:

```sql
-- Downloadable releases: the permission as it stood when this grant was created.
--
-- Captured at settlement and never updated. An artist switching downloads off affects FUTURE sales
-- only — PRD OQ-8 already preserves owners' downloads through a takedown, a more adversarial event
-- than changing one's mind.
--
-- DEFAULT false only so the column can be NOT NULL on an existing table; every write sets it
-- explicitly. false rather than true because if a future insert path forgets to set it, the grant
-- denies a download rather than granting one the artist never agreed to.
ALTER TABLE ownership_grant ADD COLUMN downloadable boolean NOT NULL DEFAULT false;
```

- [ ] **Step 2: Write the failing test**

```java
package org.shakvilla.beatzmedia.commerce.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The grant records the permission as it stood at purchase.
 *
 * <p>{@link #anExistingGrantKeepsItsPermissionWhenTheArtistLaterRefuses()} is the executable
 * statement of the grandfathering rule — the one decision a later "simplification" is most likely
 * to undo by reading the release instead of the grant.
 */
@Tag("unit")
class GrantDownloadPermissionTest {

  @Test
  void aGrantFromADownloadableReleaseIsDownloadable() {
    seedRelease("r1", true);

    grantOwnershipService.grantForSettledOrder("BZ-2026-00001", "pi_1", "mtn");

    assertTrue(ownershipRepository.findByTrack("t1").isDownloadable());
  }

  @Test
  void aGrantFromANonDownloadableReleaseIsNot() {
    seedRelease("r1", false);

    grantOwnershipService.grantForSettledOrder("BZ-2026-00002", "pi_2", "mtn");

    assertFalse(ownershipRepository.findByTrack("t1").isDownloadable());
  }

  @Test
  void anExistingGrantKeepsItsPermissionWhenTheArtistLaterRefuses() {
    seedRelease("r1", true);
    grantOwnershipService.grantForSettledOrder("BZ-2026-00003", "pi_3", "mtn");

    seedRelease("r1", false); // the artist changes their mind

    assertTrue(
        ownershipRepository.findByTrack("t1").isDownloadable(),
        "a permission captured at purchase must survive the artist changing it afterwards");
  }
}
```

Wire `seedRelease`, `grantOwnershipService` and `ownershipRepository` from the fakes used by the existing commerce service tests.

- [ ] **Step 3: Run it and confirm it fails**

```bash
cd backend && ./mvnw -o test -Dtest=GrantDownloadPermissionTest
```

- [ ] **Step 4: Thread the permission through**

In `OwnershipGrant.java`, add `private final boolean downloadable;` to the constructor and both factories, plus `public boolean isDownloadable()`.

In `GrantOwnershipService`, read the release's `downloadable` once per order line and pass it into every grant that line expands to (INV-2 album expansion — all constituent track grants carry the parent release's value).

In `OwnershipGrantEntity.java`, add `@Column(name = "downloadable", nullable = false) public boolean downloadable;` and map it both ways.

- [ ] **Step 5: Run it and confirm it passes**

```bash
cd backend && ./mvnw -o test -Dtest=GrantDownloadPermissionTest
```

Expected: `Tests run: 3, Failures: 0`

- [ ] **Step 6: Commit**

```bash
cd backend && ./mvnw -o spotless:apply
git add backend/src/main/resources/db/migration backend/src/main/java/org/shakvilla/beatzmedia/commerce backend/src/test/java/org/shakvilla/beatzmedia/commerce
git commit -m "feat(download): capture the download permission on the ownership grant"
```

---

## Task 6: Playback — the download endpoint

**Files:**
- Create: `playback/application/port/in/GetDownloadUrl.java`, `DownloadUrlResult.java`
- Create: `playback/application/port/out/DownloadPermissionReader.java`
- Create: `playback/application/service/GetDownloadUrlService.java`
- Create: `playback/domain/DownloadNotAllowedException.java`, `DownloadNotReadyException.java`
- Create: `playback/adapter/out/integration/DownloadPermissionReaderAdapter.java`
- Modify: `playback/adapter/in/rest/PlaybackResource.java`
- Modify: `platform/adapter/in/rest/DomainExceptionMapper.java`
- Modify: `platform/domain/ErrorCode.java`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/playback/application/GetDownloadUrlServiceTest.java`

**Interfaces:**
- Consumes: `OwnershipGrant.isDownloadable()` (Task 5); `DeliveryVariant.LOSSLESS` (Task 1); `MediaService.issueSignedUrl(MediaAssetId, DeliveryVariant, Duration)`
- Produces: `GET /v1/tracks/{id}/download → { downloadUrl, expiresAt, format, sizeBytes }`

- [ ] **Step 1: Write the failing test — the guard table**

```java
package org.shakvilla.beatzmedia.playback.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The four guards, each asserted separately so a failure names which one broke.
 *
 * <p>{@link #theGrantIsTheAuthorityNotTheRelease()} guards the design's most fragile decision:
 * checking the release here would retract downloads from people who already paid.
 */
@Tag("unit")
class GetDownloadUrlServiceTest {

  @Test
  void anOwnerWithPermissionGetsALosslessUrl() {
    owns("acct-1", "t1", true);
    hasLossless("t1");

    DownloadUrlResult r = service.getDownloadUrl(new TrackId("t1"), new AccountId("acct-1"));

    assertEquals("flac", r.format());
    assertEquals(DeliveryVariant.LOSSLESS, mediaService.lastVariantRequested());
  }

  @Test
  void aNonOwnerIsRefused() {
    doesNotOwn("acct-2", "t1");

    assertThrows(NotOwnedException.class,
        () -> service.getDownloadUrl(new TrackId("t1"), new AccountId("acct-2")));
  }

  @Test
  void anOwnerWhoseGrantForbidsDownloadIsRefused() {
    owns("acct-1", "t1", false);
    hasLossless("t1");

    assertThrows(DownloadNotAllowedException.class,
        () -> service.getDownloadUrl(new TrackId("t1"), new AccountId("acct-1")));
  }

  @Test
  void anAssetWithNoLosslessRenditionIsRefusedRatherThanServedTheAac() {
    owns("acct-1", "t1", true);
    hasNoLossless("t1");

    assertThrows(DownloadNotReadyException.class,
        () -> service.getDownloadUrl(new TrackId("t1"), new AccountId("acct-1")));
  }

  @Test
  void theGrantIsTheAuthorityNotTheRelease() {
    owns("acct-1", "t1", true);   // grant says yes
    releaseSaysDownloadable("t1", false); // artist has since said no
    hasLossless("t1");

    // Must still succeed: the permission was captured at purchase.
    assertEquals("flac",
        service.getDownloadUrl(new TrackId("t1"), new AccountId("acct-1")).format());
  }
}
```

- [ ] **Step 2: Run it and confirm it fails**

```bash
cd backend && ./mvnw -o test -Dtest=GetDownloadUrlServiceTest
```

- [ ] **Step 3: Create the ports and exceptions**

`DownloadPermissionReader.java`:

```java
package org.shakvilla.beatzmedia.playback.application.port.out;

import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Output port: may this account download this track?
 *
 * <p>Answered from the caller's own ownership GRANT, not from the release. The release's current
 * setting governs future sales only; reading it here would retract a download from someone who
 * already paid for it.
 *
 * <p>The adapter calls commerce's input port in-process — playback never reads commerce tables
 * (same rule as {@link OwnershipReader}).
 */
public interface DownloadPermissionReader {

  /** {@code true} only when an active grant exists AND that grant permits downloading. */
  boolean mayDownload(AccountId account, TrackId track);
}
```

`DownloadNotAllowedException` and `DownloadNotReadyException` extend whatever base the module's other domain exceptions use (see `TrackNotFoundException` in the same package).

`DownloadUrlResult.java`:

```java
public record DownloadUrlResult(String downloadUrl, Instant expiresAt, String format, long sizeBytes) {}
```

- [ ] **Step 4: Implement the service**

`GetDownloadUrlService` mirrors `GetStreamUrlService`'s constructor shape (`CatalogReader`, the new `DownloadPermissionReader`, `MediaService`, `@ConfigProperty beatz.signed-url-ttl-seconds`). The body, in guard order:

```java
  @Override
  public DownloadUrlResult getDownloadUrl(TrackId track, AccountId caller) {
    catalogReader.getTrack(track).orElseThrow(() -> new TrackNotFoundException(track.value()));

    // The grant is the authority. Do not substitute release.downloadable here.
    if (!downloadPermissionReader.mayDownload(caller, track)) {
      throw new DownloadNotAllowedException(track.value());
    }

    MediaAssetId asset = mediaService
        .findAssetIdForOwner(new OwnerRef("track:" + track.value()))
        .orElseThrow(() -> new DownloadNotReadyException(track.value()));

    SignedUrl signed;
    try {
      signed = mediaService.issueSignedUrl(
          asset, DeliveryVariant.LOSSLESS, Duration.ofSeconds(signedUrlTtlSeconds));
    } catch (IllegalStateException e) {
      // resolveDeliveryKey throws when losslessKey is null — no FLAC rendition yet.
      throw new DownloadNotReadyException(track.value());
    }

    return new DownloadUrlResult(signed.url(), signed.expiresAt(), "flac", signed.sizeBytes());
  }
```

If `SignedUrl` carries no size, add `sizeBytes` to it from the object store's metadata rather than inventing a number — an unknown size must not be reported as `0`.

`DownloadPermissionReaderAdapter` lives in `playback/adapter/out/integration/` and calls commerce's input port, exactly as `OwnershipReaderAdapter` does.

- [ ] **Step 5: Register the error codes**

Add `DOWNLOAD_NOT_ALLOWED` and `DOWNLOAD_NOT_READY` to `ErrorCode.java`, and map both to `409` in `DomainExceptionMapper.java` beside the existing entries.

- [ ] **Step 6: Add the endpoint**

In `PlaybackResource.java`, beside `getStreamUrl`:

```java
  /** GET /v1/tracks/:id/download — the lossless file, owners with permission only. */
  @GET
  @Path("/tracks/{id}/download")
  @Authenticated
  public DownloadUrlResponse getDownloadUrl(@PathParam("id") String id) {
    AccountId caller = callerId().orElseThrow(NotAuthenticatedException::new);
    DownloadUrlResult r = getDownloadUrl.getDownloadUrl(new TrackId(id), caller);
    return new DownloadUrlResponse(r.downloadUrl(), r.expiresAt(), r.format(), r.sizeBytes());
  }
```

- [ ] **Step 7: Run and confirm green**

```bash
cd backend && ./mvnw -o test -Dtest=GetDownloadUrlServiceTest
```

Expected: `Tests run: 5, Failures: 0`

- [ ] **Step 8: Mutation-check the grandfathering guard**

Temporarily change the service to read the release's current `downloadable` instead of the grant. Re-run. `theGrantIsTheAuthorityNotTheRelease` **must** fail. Revert.

- [ ] **Step 9: Commit**

```bash
cd backend && ./mvnw -o spotless:apply
git add backend/src/main/java/org/shakvilla/beatzmedia backend/src/test/java/org/shakvilla/beatzmedia/playback
git commit -m "feat(download): GET /v1/tracks/:id/download serves the lossless file to permitted owners"
```

---

## Task 7: Backend — expose the flag on read models

**Files:**
- Modify: catalog's track/album/release view records + DTOs, and `admin/application/port/in/CatalogItemDetailView.java` + `admin/adapter/in/rest/CatalogItemDetailDto.java`
- Modify: `API-CONTRACT.md`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/admin/it/AdminCatalogContractTest.java`

**Interfaces:**
- Produces: `downloadable: boolean` on the track/album/release read models and on admin catalog detail

- [ ] **Step 1: Add `downloadable` to the views the SPA reads**

Add the field to the catalog track/album/release view records and their DTOs, sourced from the release. Follow the `genre` field added in the same DTO for the exact pattern.

- [ ] **Step 2: Extend the admin contract test**

In `AdminCatalogContractTest`, bump the expected record-component count and add:

```java
    assertTrue(names.contains("downloadable"));
    assertEquals(true, dto.downloadable(), "moderators must see whether a release permits downloads");
```

- [ ] **Step 3: Run it**

```bash
cd backend && ./mvnw -o test -Dtest=AdminCatalogContractTest
```

- [ ] **Step 4: Document the endpoint in `API-CONTRACT.md`**

Add to the playback section:

```
| GET | `/tracks/:id/download` | lossless file for a permitted owner | `{ downloadUrl, expiresAt, format, sizeBytes }` |
```

with a note: `409 DOWNLOAD_NOT_ALLOWED` when the caller's grant forbids it, `409 DOWNLOAD_NOT_READY` when no FLAC rendition exists yet, `403 NOT_OWNED` otherwise. State that the permission is captured at purchase and that an artist's later change affects future sales only.

- [ ] **Step 5: Commit**

```bash
cd backend && ./mvnw -o spotless:apply
git add backend/src/main API-CONTRACT.md backend/src/test
git commit -m "feat(download): expose downloadable on catalog and admin read models"
```

---

## Task 8: Frontend — the artist's choice

**Files:**
- Modify: `Frontend/src/routes/studio.release.new.details.tsx`
- Modify: `Frontend/src/routes/studio.release.$releaseId.tsx`
- Modify: `Frontend/src/features/studio/release-draft-context.tsx`
- Modify: `Frontend/src/lib/api/mappers.ts`
- Test: `Frontend/src/lib/api/mappers.test.ts`

**Interfaces:**
- Consumes: `downloadable` on the wire types (Task 7)
- Produces: the draft carries `downloadable: boolean | null`

- [ ] **Step 1: Add the field to the wire and domain types**

In `mappers.ts`, add `downloadable: boolean | null` to the release/track wire interfaces and their mapped types, and carry it through the `to*` functions with `?? null`.

- [ ] **Step 2: Add a failing mapper test**

```ts
it('carries a missing download choice through as null rather than false', () => {
  const wire: StudioReleaseWire = { /* …existing fixture fields…, */ downloadable: null }
  expect(toStudioRelease(wire).downloadable).toBeNull()
})
```

The distinction matters: `false` means "the artist said no", `null` means "not chosen yet", and only the latter blocks publish.

- [ ] **Step 3: Run and confirm it fails, then passes**

```bash
export PATH="$HOME/.nvm/versions/node/v22.17.1/bin:$PATH"
cd Frontend && npx vitest run src/lib/api/mappers.test.ts
```

- [ ] **Step 4: Add the required choice to the Details step**

In `studio.release.new.details.tsx`, add a two-option control with **no pre-selection**:

```tsx
{/*
  No default. A default would decide this for the artist by inertia — off-by-default quietly
  makes the platform stream-only, on-by-default means "the artist chooses" becomes "the artist
  notices". The publish button stays disabled until one is picked.
*/}
<Field label="Downloads" required>
  <Choice
    value={draft.downloadable}
    onChange={(v) => setDraft({ ...draft, downloadable: v })}
    options={[
      { value: true,  label: 'Allow downloads',  hint: 'Buyers get a lossless file they keep.' },
      { value: false, label: 'Streaming only',   hint: 'Buyers can play it in the app, but not download the file.' },
    ]}
  />
</Field>
```

Use whatever radio/segmented control the wizard already uses for its other choices rather than introducing a new component.

- [ ] **Step 5: Block publish until answered**

In the review step, disable the publish button when `draft.downloadable === null`, with the reason shown — never a silently disabled button.

- [ ] **Step 6: Make it editable afterwards**

In `studio.release.$releaseId.tsx`, show the current setting as editable with the copy: *"Changing this affects future sales. People who already bought this keep the downloads they paid for."*

- [ ] **Step 7: Run the gate**

```bash
export PATH="$HOME/.nvm/versions/node/v22.17.1/bin:$PATH"
cd Frontend && npm run build && npx vitest run
```

- [ ] **Step 8: Commit**

```bash
git add Frontend/src
git commit -m "feat(download): artists choose whether a release may be downloaded"
```

---

## Task 9: Frontend — disclosure, the download action, and the dead controls

**Files:**
- Modify: `Frontend/src/routes/track.$trackId.tsx`, `Frontend/src/routes/album/$albumId.tsx`
- Modify: `Frontend/src/routes/store.hifi.tsx`
- Modify: `Frontend/src/routes/library.tsx`
- Modify: `Frontend/src/routes/settings.tsx`
- Modify: `Frontend/src/lib/api/queries/catalog.ts`

**Interfaces:**
- Consumes: `downloadable` on the mapped types (Task 8); `GET /v1/tracks/:id/download` (Task 6)

- [ ] **Step 1: Add the download query**

In `queries/catalog.ts`:

```ts
/** `GET /v1/tracks/:id/download` — a signed lossless URL for a permitted owner. */
export function apiDownloadTrack(id: string): Promise<DownloadUrlWire> {
  return apiFetch<DownloadUrlWire>(`/tracks/${id}/download`)
}
```

- [ ] **Step 2: Show the marker wherever the release is sold**

On track and album detail, render `Download available` when `downloadable`, `Streaming only` when not. Because the permission is fixed at purchase, this is part of what the buyer is agreeing to — it must appear before checkout, not after.

- [ ] **Step 3: Fix the Hi-Fi copy**

In `store.hifi.tsx`, change the subtitle from `Studio-grade masters, downloaded and owned forever` to `Studio-grade lossless masters, owned forever` — the tier keeps its lossless promise and stops promising downloads universally, since artists may now opt out.

- [ ] **Step 4: Add the library download action**

In `library.tsx`, for each owned track whose grant permits it, a download action calling `apiDownloadTrack` and navigating to the returned `downloadUrl`. Show nothing when it is not permitted — never a disabled control with no explanation.

- [ ] **Step 5: Remove the dead Settings controls**

Delete the `Download quality` row and its `downloadQuality` state: one delivery format means there is nothing to choose, so it is a control that does nothing. Replace the hardcoded `${ownedTracks.length} tracks · 1.4 GB` with the track count alone — the byte figure was invented.

- [ ] **Step 6: Run the gate**

```bash
export PATH="$HOME/.nvm/versions/node/v22.17.1/bin:$PATH"
cd Frontend && npm run build && npx vitest run
```

- [ ] **Step 7: Commit**

```bash
git add Frontend/src
git commit -m "feat(download): disclose the download permission and wire the library download"
```

---

## Task 10: Integration test — the guard table against a real database

**Files:**
- Create: `backend/src/test/java/org/shakvilla/beatzmedia/playback/it/DownloadEndpointIT.java`

**Interfaces:**
- Consumes: everything above

- [ ] **Step 1: Write the IT**

Cover, each as its own test against Testcontainers Postgres + MinIO:

1. owner + permitted + rendition present → `200` with a URL ending `.flac`
2. anonymous → `401`
3. authenticated non-owner → `403 NOT_OWNED`
4. owner whose grant forbids → `409 DOWNLOAD_NOT_ALLOWED`
5. owner, permitted, asset has no `lossless_key` → `409 DOWNLOAD_NOT_READY`
6. **grant permits, release since flipped to false → still `200`** (grandfathering, end to end)

- [ ] **Step 2: Run it**

```bash
cd backend && ./mvnw -o verify -DskipITs=false -Dit.test=DownloadEndpointIT
```

Expected: 6 tests, `Skipped: 0`.

- [ ] **Step 3: Commit**

```bash
cd backend && ./mvnw -o spotless:apply
git add backend/src/test/java/org/shakvilla/beatzmedia/playback/it/DownloadEndpointIT.java
git commit -m "test(download): assert the download guard table against a real database"
```

---

## Task 11: Docs and the verification gate

**Files:**
- Modify: `BACKEND-PRD.md` §3.3 (invariants)
- Modify: `backend/docs/architecture/media.md`, `backend/docs/architecture/playback.md`, `backend/docs/architecture/commerce.md`
- Modify: `docs/qa/2026-08-08-admin-gap-report.md`

- [ ] **Step 1: Number the invariant**

Add to `BACKEND-PRD.md` §3.3, using the next free INV number:

> **INV-N — A download is served only when the caller's own grant permits it.** Server-enforced, exactly as INV-3. The permission is captured on the ownership grant at settlement; an artist's later change affects future sales only.

- [ ] **Step 2: Update the three module ADDs**

Media: the LOSSLESS rendition and why it is not FULL. Playback: the download endpoint and its guard order. Commerce: the captured permission and the grandfathering rule.

- [ ] **Step 3: Close GAP-30's download entry**

In the QA report's triage list, mark the "no way to download anything you have bought" item resolved for single tracks, and note that the ZIP bundle remains outstanding.

- [ ] **Step 4: Commit**

```bash
git add BACKEND-PRD.md backend/docs docs/qa
git commit -m "docs(download): record the download invariant and update the module ADDs"
```

- [ ] **Step 5: Ask the user to run the gate**

Tell the user:

> The branch is ready. Please run `bash backend/scripts/verify.sh && bash backend/scripts/smoke.sh` and paste the result.

**Do not run these yourself** — IntelliJ's background compiler races the Maven build and produces failures that are not real.

- [ ] **Step 6: Open the PR once the gate is green**

Use the `open-pull-request` skill. Note in the PR body that `contract-test` will pass without verifying the new `API-CONTRACT.md` entry, because that gate runs zero tests (issue #210).

---

## Self-Review

**Spec coverage**

| Spec requirement | Task |
|---|---|
| Decision 1 — per release | 4 |
| Decision 2 — captured at purchase, grandfathered | 5, 6 (guard + mutation check), 10 (end to end) |
| Decision 3 — no default, publish guard | 4, 8 |
| Decision 4 — disclosure, Hi-Fi copy | 9 |
| Decision 5 — FLAC rendition | 1, 2, 3 |
| Decision 6 — ZIP deferred | out of scope, noted in Task 11 |
| Decision 7 — remove dead Settings controls | 9 |
| Data model (3 columns) | 2, 4, 5 |
| Endpoint + 4 guards | 6, 10 |
| Invariant | 11 |
| Testing table | 1, 3, 4, 5, 6, 10 |

No gaps.

**Placeholders:** none — every code step carries real code; fixtures point at the existing test files to copy rather than inventing new harnesses.

**Type consistency:** `downloadable` (boolean, nullable on release, non-null on grant) used consistently; `DeliveryVariant.LOSSLESS`, `markLosslessReady`, `getLosslessKey`, `transcodeLossless`, `mayDownload`, `DownloadUrlResult` all defined once and referenced with the same names throughout.
