# Audio Playback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make BeatzClik play real audio — replace HLS delivery with a single presigned file per variant, and drive the fan player from a real `<audio>` element instead of a simulated timer.

**Architecture:** The backend transcoder stops emitting HLS (`playlist.m3u8` + relative, unsigned `.ts` segments that 403 against a private bucket) and emits one AAC/M4A object per variant instead, so a single presigned URL is fully playable. On the frontend, a single `<audio>` element owned by `PlayerProvider` becomes the clock: `timeupdate`/`ended`/`error` drive the reducer, which keeps only intent (queue, playing, repeat). The client-side `PREVIEW_SECONDS` constant is deleted — a non-owner is signed a physically 30-second file, so INV-3 enforces itself.

**Tech Stack:** Java 25 / Quarkus 3.36 / ffmpeg (bundled in `Dockerfile.jvm`) / S3 (MinIO) · React 19 / TanStack Query v5 / TanStack Router / Vitest

**Spec:** `docs/superpowers/specs/2026-08-01-audio-playback-design.md` (commit `0895e5e`)

## Global Constraints

- Branch: `feat/audio-playback` off `master`. One PR.
- **NEVER stage `backend/src/main/resources/application.properties` or `backend/docker-compose.yml`.** Both are dirty with local dev config; `git add` by explicit path only.
- Backend gate: `bash backend/scripts/verify.sh && bash backend/scripts/smoke.sh` — **run by the USER**, not the implementer. Do not run maven builds; report and ask.
- Frontend gate (from `Frontend/`, Node 22 via nvm): `npm run build` (`tsc -b`) and `npx vitest run` must both be green.
- Delivery format: **AAC in M4A**, `-c:a aac -b:a 128k`. Object keys `delivery/{id}/full.m4a` and `delivery/{id}/preview.m4a`. Content type `audio/mp4`.
- **No Flyway migration.** `media_asset.hls_key` / `preview_key` are opaque `varchar(255)` object keys; the column names stay, only the Java field is renamed.
- Hexagonal rule holds: `adapter → application → domain`. Domain imports no framework.
- **Anti-fabrication acceptance criterion:** when a stream is unavailable the UI shows an unavailable state and disables play. It must never animate a progress bar over silence.
- A test named for a behaviour must assert that behaviour.

## File Structure

**Backend (`backend/src/main/java/org/shakvilla/beatzmedia/media/`)**

| File | Responsibility | Change |
|---|---|---|
| `application/port/out/AudioTranscoderPort.java` | transcoder contract | rename methods `transcodeHls`→`transcodeFull`, `clipPreviewHls`→`clipPreview` |
| `adapter/out/integration/FfmpegAudioTranscoderAdapter.java` | ffmpeg invocation + upload | emit one `.m4a` per variant; delete HLS flags and `uploadHlsDir` |
| `domain/MediaAsset.java` | asset state + `resolveDeliveryKey` (INV-3) | field `hlsKey` → `fullKey` |
| `adapter/out/persistence/MediaAssetEntity.java` | JPA row | field `hlsKey` → `fullKey`, still `@Column(name = "hls_key")` |
| `adapter/out/persistence/MediaAssetMapper.java` | entity ↔ domain | follow the rename |
| `application/service/MediaApplicationService.java` | orchestration | call the renamed port methods |

**Backend tests**

| File | Change |
|---|---|
| `media/it/RealTranscodeIT.java` | assert `.m4a` objects instead of playlists |
| `media/it/MediaDeliveryIT.java` | fixture keys `…/full.m4a`, `…/preview.m4a` |

**Frontend (`Frontend/src/`)**

| File | Responsibility | Change |
|---|---|---|
| `lib/api/queries/playback.ts` | `GET /v1/tracks/:id/stream` | **create** |
| `lib/api/queries/playback.test.ts` | query contract test | **create** |
| `features/player/player-context.tsx` | reducer (intent) + `<audio>` (time) | export reducer; `TICK`→`SET_PROGRESS`; add `ENDED`/`STREAM_ERROR`; delete `PREVIEW_SECONDS` and the interval; render the element |
| `features/player/player-context.test.ts` | reducer unit tests | **create** |
| `components/layout/player-bar.tsx` | unavailable state, real duration | modify |

---

### Task 1: Transcoder emits a single M4A per variant

**Files:**
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/media/application/port/out/AudioTranscoderPort.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/media/adapter/out/integration/FfmpegAudioTranscoderAdapter.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/media/application/service/MediaApplicationService.java`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/media/it/RealTranscodeIT.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `AudioTranscoderPort.transcodeFull(ObjectKey original, MediaAssetId id) → ObjectKey` and `AudioTranscoderPort.clipPreview(ObjectKey original, MediaAssetId id, int previewSeconds) → ObjectKey`. Returned keys are `delivery/{id}/full.m4a` and `delivery/{id}/preview.m4a` in the delivery bucket. Task 2 and Task 3 call these names.

- [ ] **Step 1: Update the failing IT to expect single files**

In `RealTranscodeIT.java`, replace the body of `transcode_wav_produces_hls_and_preview_renditions` and rename it:

```java
  @Test
  void transcode_wav_produces_full_and_preview_m4a() throws Exception {
    MediaAssetId id = new MediaAssetId("asset-transcode-1");
    ObjectKey originalKey = objectStore.putOriginal(/* keep the existing fixture-upload
        arguments from this test verbatim — read the file, do not invent them */);

    ObjectKey fullKey = transcoder.transcodeFull(originalKey, id);
    assertNotNull(fullKey, "full key must not be null");
    assertTrue(fullKey.key().endsWith("/full.m4a"), "full rendition must be a single .m4a: " + fullKey.key());
    assertTrue(objectStore.exists(fullKey), "full rendition must exist in delivery bucket");

    ObjectKey previewKey = transcoder.clipPreview(originalKey, id, 30);
    assertNotNull(previewKey, "preview key must not be null");
    assertTrue(previewKey.key().endsWith("/preview.m4a"), "preview must be a single .m4a: " + previewKey.key());
    assertTrue(objectStore.exists(previewKey), "preview rendition must exist in delivery bucket");
  }
```

The existing test uploads its fixture via `objectStore.putOriginal(...)` at around line 109. Copy that call verbatim — only the assertions below it change.

- [ ] **Step 2: Ask the user to run the backend gate and confirm this test FAILS**

Report: "Task 1 step 2 — `RealTranscodeIT.transcode_wav_produces_full_and_preview_m4a` should fail to compile (`transcodeFull` does not exist yet). Please run `bash backend/scripts/verify.sh` and paste the result."

Expected: compilation error — `cannot find symbol: method transcodeFull`.

Do **not** run maven yourself.

- [ ] **Step 3: Rename the port methods**

In `AudioTranscoderPort.java`, rename the two methods and update their javadoc:

```java
  /**
   * Transcode the original to the FULL delivery rendition: a single AAC/M4A object at
   * {@code delivery/{id}/full.m4a}. Single-file (not HLS) so one presigned URL is fully
   * playable — an HLS playlist's segments are referenced relatively and would be unsigned
   * against the private delivery bucket. Media ADD §4.
   */
  ObjectKey transcodeFull(ObjectKey original, MediaAssetId id);

  /**
   * Transcode the first {@code previewSeconds} of the original to the PREVIEW rendition, a
   * single AAC/M4A object at {@code delivery/{id}/preview.m4a}. The clip IS the enforcement:
   * a non-owner is only ever signed this object, so INV-3 cannot be overrun client-side.
   */
  ObjectKey clipPreview(ObjectKey original, MediaAssetId id, int previewSeconds);
```

- [ ] **Step 4: Replace the adapter's two methods**

In `FfmpegAudioTranscoderAdapter.java`, replace `transcodeHls` and `clipPreviewHls` with:

```java
  @Override
  public ObjectKey transcodeFull(ObjectKey original, MediaAssetId id) {
    return transcodeToM4a(original, id, null, "full.m4a", "full-");
  }

  @Override
  public ObjectKey clipPreview(ObjectKey original, MediaAssetId id, int previewSeconds) {
    return transcodeToM4a(original, id, previewSeconds, "preview.m4a", "preview-");
  }

  /**
   * Shared path for both renditions: download the original, run ffmpeg to one AAC/M4A file,
   * upload it, return its key. {@code durationLimit} non-null clips the output (preview).
   */
  private ObjectKey transcodeToM4a(
      ObjectKey original, MediaAssetId id, Integer durationLimit, String filename, String tmpPrefix) {
    Path tmpInput = null;
    Path tmpOutput = null;
    try {
      tmpInput = downloadToTemp(original, tmpPrefix, ".audio");
      tmpOutput = Files.createTempFile(tmpPrefix + id.value(), ".m4a");

      runFfmpegM4a(tmpInput, tmpOutput, durationLimit);

      String relKey = "delivery/" + id.value() + "/" + filename;
      try (InputStream in = Files.newInputStream(tmpOutput)) {
        objectStore.putDelivery(id, relKey, in, "audio/mp4");
      }
      return new ObjectKey(deliveryBucketOf(original), relKey);
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("ffmpeg transcode failed for " + id.value(), e);
    } finally {
      deleteSilently(tmpOutput);
      deleteSilently(tmpInput);
    }
  }
```

- [ ] **Step 5: Replace `runFfmpegHls` with `runFfmpegM4a`**

Delete `runFfmpegHls` and `uploadHlsDir` entirely. Add:

```java
  private void runFfmpegM4a(Path inputFile, Path outputFile, Integer durationLimit)
      throws IOException, InterruptedException {
    List<String> cmd = new ArrayList<>();
    cmd.add("ffmpeg");
    cmd.add("-y");
    cmd.add("-i"); cmd.add(inputFile.toAbsolutePath().toString());
    if (durationLimit != null) {
      cmd.add("-t"); cmd.add(String.valueOf(durationLimit));
    }
    cmd.add("-vn");                              // audio only — drop any cover-art video stream
    cmd.add("-c:a"); cmd.add("aac");
    cmd.add("-b:a"); cmd.add("128k");
    cmd.add("-movflags"); cmd.add("+faststart"); // moov atom first: playable before fully downloaded
    cmd.add(outputFile.toAbsolutePath().toString());

    Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
    String output = new String(proc.getInputStream().readAllBytes()).trim();
    int exit = proc.waitFor();
    if (exit != 0) {
      throw new IllegalStateException("ffmpeg exited with " + exit + ": " + output);
    }
  }
```

Also delete the now-unused `HLS_SEGMENT_DURATION` constant and any now-unused imports (`Stream`, `deleteDirSilently` if nothing else calls it — check before deleting).

- [ ] **Step 6: Update the two call sites in `MediaApplicationService`**

Find the calls to `transcodeHls(` and `clipPreviewHls(` and rename them to `transcodeFull(` and `clipPreview(`. Do not change any surrounding logic.

- [ ] **Step 7: Ask the user to run the backend gate and confirm PASS**

Report: "Task 1 step 7 — please run `bash backend/scripts/verify.sh` and paste the result. Expecting `RealTranscodeIT.transcode_wav_produces_full_and_preview_m4a` to pass."

If `MediaDeliveryIT` fails here, that is expected — Task 2 fixes it. Note it and continue.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/org/shakvilla/beatzmedia/media/application/port/out/AudioTranscoderPort.java backend/src/main/java/org/shakvilla/beatzmedia/media/adapter/out/integration/FfmpegAudioTranscoderAdapter.java backend/src/main/java/org/shakvilla/beatzmedia/media/application/service/MediaApplicationService.java backend/src/test/java/org/shakvilla/beatzmedia/media/it/RealTranscodeIT.java
git commit -m "feat(media): transcode to a single AAC/M4A per variant instead of HLS"
```

---

### Task 2: Rename `hlsKey` → `fullKey` and fix the delivery IT

**Files:**
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/media/domain/MediaAsset.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/media/adapter/out/persistence/MediaAssetEntity.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/media/adapter/out/persistence/MediaAssetMapper.java`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/media/it/MediaDeliveryIT.java`

**Interfaces:**
- Consumes: Task 1's `delivery/{id}/full.m4a` key convention.
- Produces: `MediaAsset.fullKey` (was `hlsKey`). `resolveDeliveryKey(DeliveryVariant)` keeps its signature and behaviour. No later task depends on the rename.

**Why:** a field named `hlsKey` holding an `.m4a` key is a misleading-name trap. The DB column stays `hls_key` — renaming a merged column would need a migration for zero behavioural gain.

- [ ] **Step 1: Update the delivery IT fixtures to the new keys**

In `MediaDeliveryIT.java`, change the two fixture keys and their assertions:

```java
    byte[] content = "full rendition bytes".getBytes();
    String relKey = "delivery/" + id.value() + "/full.m4a";
```

```java
    assertEquals("full rendition bytes", response.body());
```

and for the preview case:

```java
    byte[] content = "preview rendition bytes".getBytes();
    String relKey = "delivery/" + id.value() + "/preview.m4a";
```

```java
    assertEquals("preview rendition bytes", response.body());
```

Read the file first — keep the surrounding setup exactly as it is; only the key strings and the asserted body text change.

- [ ] **Step 2: Rename the domain field**

In `MediaAsset.java`, rename the private field `hlsKey` to `fullKey` and every reference to it, including inside `resolveDeliveryKey`:

```java
    if (variant == DeliveryVariant.FULL) {
      if (fullKey == null) {
        throw new IllegalStateException("fullKey is null for asset " + id.value());
      }
      return fullKey;
    }
```

Rename any getter/wither (`getHlsKey`/`withHlsKey`) to match. Do not change the READY check or the FULL/PREVIEW branching — that is INV-3.

- [ ] **Step 3: Rename the entity field, keeping the column**

In `MediaAssetEntity.java`:

```java
  /**
   * FULL delivery object key. The column is still named {@code hls_key} for historical
   * reasons — renaming a merged column would require a migration for no behavioural gain.
   * Since 2026-08-01 this holds a single {@code .m4a} object key, not an HLS playlist.
   */
  @Column(name = "hls_key", length = 255)
  public String fullKey;
```

- [ ] **Step 4: Follow the rename in the mapper**

In `MediaAssetMapper.java`, update both directions to use `fullKey`.

- [ ] **Step 5: Ask the user to run the backend gate and confirm PASS**

Report: "Task 2 step 5 — please run `bash backend/scripts/verify.sh && bash backend/scripts/smoke.sh` and paste the result. Expecting `MediaDeliveryIT` and `RealTranscodeIT` green, and Compose to boot healthy."

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/shakvilla/beatzmedia/media/domain/MediaAsset.java backend/src/main/java/org/shakvilla/beatzmedia/media/adapter/out/persistence/MediaAssetEntity.java backend/src/main/java/org/shakvilla/beatzmedia/media/adapter/out/persistence/MediaAssetMapper.java backend/src/test/java/org/shakvilla/beatzmedia/media/it/MediaDeliveryIT.java
git commit -m "refactor(media): rename hlsKey to fullKey; column stays hls_key"
```

---

### Task 3: Frontend stream query

**Files:**
- Create: `Frontend/src/lib/api/queries/playback.ts`
- Create: `Frontend/src/lib/api/queries/playback.test.ts`

**Interfaces:**
- Consumes: `apiFetch` from `Frontend/src/lib/api/client.ts` (prepends `/v1`).
- Produces: `trackStreamQuery(trackId: string)` returning `queryOptions` whose data is `TrackStream { audioUrl: string; previewSeconds: number | null; expiresAt: string | null }`. Task 4 calls this.

- [ ] **Step 1: Write the failing test**

Create `Frontend/src/lib/api/queries/playback.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { trackStreamQuery } from './playback'
import { apiFetch } from '../client'

vi.mock('../client')

const ctx = {} as never

beforeEach(() => vi.resetAllMocks())

describe('trackStreamQuery', () => {
  it('fetches /tracks/:id/stream and maps the wire shape', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      audioUrl: 'https://minio.local/full.m4a?sig=abc',
      previewSeconds: 30,
      expiresAt: '2026-08-01T12:00:00Z',
    })

    const result = await trackStreamQuery('last-last').queryFn!(ctx)

    expect(apiFetch).toHaveBeenCalledWith('/tracks/last-last/stream')
    expect(result).toEqual({
      audioUrl: 'https://minio.local/full.m4a?sig=abc',
      previewSeconds: 30,
      expiresAt: '2026-08-01T12:00:00Z',
    })
  })

  it('encodes the track id into the path', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ audioUrl: 'u', previewSeconds: null, expiresAt: null })

    await trackStreamQuery('a/b').queryFn!(ctx)

    expect(apiFetch).toHaveBeenCalledWith('/tracks/a%2Fb/stream')
  })

  it('keys per track so switching tracks does not reuse a cached URL', () => {
    expect(trackStreamQuery('t1').queryKey).toEqual(['track', 't1', 'stream'])
    expect(trackStreamQuery('t2').queryKey).not.toEqual(trackStreamQuery('t1').queryKey)
  })

  it('does not retry — a 503 MEDIA_UNAVAILABLE must not retry-storm', () => {
    expect(trackStreamQuery('t1').retry).toBe(false)
  })
})
```

- [ ] **Step 2: Run the test and verify it fails**

Run from `Frontend/`:
```bash
npx vitest run src/lib/api/queries/playback.test.ts
```
Expected: FAIL — cannot resolve `./playback`.

- [ ] **Step 3: Implement the query**

Create `Frontend/src/lib/api/queries/playback.ts`:

```ts
import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'

export interface TrackStreamWire {
  audioUrl: string
  previewSeconds: number | null
  expiresAt: string | null
}

export interface TrackStream {
  audioUrl: string
  /** Length of the preview the server signed, when this is a preview stream. */
  previewSeconds: number | null
  /** ISO instant the signed URL stops working. */
  expiresAt: string | null
}

/**
 * `GET /v1/tracks/:id/stream` — a signed, time-boxed delivery URL for one track.
 *
 * The server decides FULL vs PREVIEW from ownership (INV-3); the client never asks for a
 * variant and cannot widen one. `retry: false` because the failure mode here is
 * `503 MEDIA_UNAVAILABLE` for a track with no READY asset — retrying cannot help and would
 * hammer the API for every track in a queue.
 */
export function trackStreamQuery(trackId: string) {
  return queryOptions({
    queryKey: ['track', trackId, 'stream'],
    queryFn: async (): Promise<TrackStream> => {
      const w = await apiFetch<TrackStreamWire>(`/tracks/${encodeURIComponent(trackId)}/stream`)
      return { audioUrl: w.audioUrl, previewSeconds: w.previewSeconds, expiresAt: w.expiresAt }
    },
    retry: false,
    // Signed URLs are short-lived; never serve a cached one that may already be dead.
    staleTime: 0,
    gcTime: 60_000,
  })
}
```

- [ ] **Step 4: Run the test and verify it passes**

```bash
npx vitest run src/lib/api/queries/playback.test.ts
```
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add Frontend/src/lib/api/queries/playback.ts Frontend/src/lib/api/queries/playback.test.ts
git commit -m "feat(player): add trackStreamQuery for GET /v1/tracks/:id/stream"
```

---

### Task 4: Reducer keeps intent; time comes from outside

**Files:**
- Modify: `Frontend/src/features/player/player-context.tsx`
- Create: `Frontend/src/features/player/player-context.test.ts`

**Interfaces:**
- Consumes: nothing from Task 3 yet (this task is pure reducer).
- Produces: exported `reducer(state, action)` and exported `type PlayerState` / `PlayerAction`, with new actions `{ type: 'SET_PROGRESS'; seconds: number }`, `{ type: 'ENDED'; preview: boolean }`, `{ type: 'STREAM_ERROR' }`, plus `PlayerState.unavailable: boolean` and `PlayerState.duration: number | null`. Task 5 dispatches these from the `<audio>` element.

**Why:** the element owns *time*, the reducer owns *intent*. `TICK` (add one second) becomes `SET_PROGRESS` (set to a value), which is what a real `timeupdate` gives us.

- [ ] **Step 1: Write the failing reducer tests**

Create `Frontend/src/features/player/player-context.test.ts`:

```ts
import { describe, it, expect } from 'vitest'
import { reducer, initialState, type PlayerState } from './player-context'
import type { Track } from '../../types'

const track = (id: string, duration = 180): Track =>
  ({ id, title: id, artistId: 'a', artistName: 'A', duration, image: '', ownership: 'owned' }) as Track

const playing = (over: Partial<PlayerState> = {}): PlayerState => ({
  ...initialState,
  queue: [track('t1'), track('t2')],
  currentIndex: 0,
  isPlaying: true,
  ...over,
})

describe('SET_PROGRESS', () => {
  it('sets progress to the reported position rather than incrementing', () => {
    const next = reducer(playing({ progress: 4 }), { type: 'SET_PROGRESS', seconds: 41.6 })
    expect(next.progress).toBe(41.6)
  })

  it('does not stop playback near the preview length — the file ending is what stops it', () => {
    const next = reducer(playing({ progress: 29 }), { type: 'SET_PROGRESS', seconds: 30 })
    expect(next.isPlaying).toBe(true)
    expect(next.previewHitLimit).toBe(false)
  })
})

describe('ENDED', () => {
  it('advances to the next track', () => {
    const next = reducer(playing(), { type: 'ENDED', preview: false })
    expect(next.currentIndex).toBe(1)
    expect(next.progress).toBe(0)
  })

  it('replays the same track when repeat is one', () => {
    const next = reducer(playing({ repeat: 'one' }), { type: 'ENDED', preview: false })
    expect(next.currentIndex).toBe(0)
    expect(next.progress).toBe(0)
  })

  it('stops at the end of the queue', () => {
    const next = reducer(playing({ currentIndex: 1 }), { type: 'ENDED', preview: false })
    expect(next.isPlaying).toBe(false)
  })

  it('flags the preview cap and does NOT advance when the ended stream was a preview', () => {
    const next = reducer(playing(), { type: 'ENDED', preview: true })
    expect(next.previewHitLimit).toBe(true)
    expect(next.isPlaying).toBe(false)
    expect(next.currentIndex).toBe(0)
  })
})

describe('STREAM_ERROR', () => {
  it('marks the track unavailable and stops claiming to play', () => {
    const next = reducer(playing({ progress: 12 }), { type: 'STREAM_ERROR' })
    expect(next.unavailable).toBe(true)
    expect(next.isPlaying).toBe(false)
  })
})

describe('PLAY_TRACK', () => {
  it('clears a previous unavailable state', () => {
    const next = reducer(playing({ unavailable: true }), { type: 'PLAY_TRACK', track: track('t3') })
    expect(next.unavailable).toBe(false)
  })
})
```

- [ ] **Step 2: Run the tests and verify they fail**

```bash
npx vitest run src/features/player/player-context.test.ts
```
Expected: FAIL — `reducer` and `initialState` are not exported.

- [ ] **Step 3: Export the reducer and extend the state**

In `player-context.tsx`:

1. Delete `export const PREVIEW_SECONDS = 30` and its comment.
2. Change `function reducer(` to `export function reducer(`.
3. Change `const initialState` to `export const initialState` and export the types: `export interface PlayerState`, `export type PlayerAction`.
4. Add two fields to `PlayerState`:

```ts
  /** True when the current track has no playable stream. Never animate progress in this state. */
  unavailable: boolean
  /** Real duration from the audio element once known; falls back to catalogue metadata. */
  duration: number | null
```

5. Add them to `initialState`:

```ts
  unavailable: false,
  duration: null,
```

- [ ] **Step 4: Replace `TICK` with the three new actions**

In the `PlayerAction` union, delete `| { type: 'TICK'; limited: boolean }` and add:

```ts
  | { type: 'SET_PROGRESS'; seconds: number }
  | { type: 'SET_DURATION'; seconds: number }
  | { type: 'ENDED'; preview: boolean }
  | { type: 'STREAM_ERROR' }
```

Delete the whole `case 'TICK':` block and add:

```ts
    case 'SET_PROGRESS':
      // The audio element is the clock. It reports where it actually is; we never guess,
      // and we never advance time for a stream that is not playing.
      return { ...state, progress: action.seconds }

    case 'SET_DURATION':
      return { ...state, duration: action.seconds }

    case 'ENDED': {
      // A preview stream ending IS the INV-3 cap: the server signed a ~30s file and it ran
      // out. Do not advance — the fan should be told they can buy the rest.
      if (action.preview) {
        return { ...state, isPlaying: false, previewHitLimit: true }
      }
      if (state.repeat === 'one') return { ...state, progress: 0 }
      const ni = advanceIndex(state)
      if (ni === null) return { ...state, isPlaying: false }
      return { ...state, currentIndex: ni, progress: 0, duration: null }
    }

    case 'STREAM_ERROR':
      // No playable audio. Stop, and let the UI say so instead of animating over silence.
      return { ...state, isPlaying: false, unavailable: true }
```

5. In `PLAY_TRACK`, `PLAY_QUEUE`, `NEXT` and `PREV`, add `unavailable: false, duration: null` to the returned state so a new track starts clean.

- [ ] **Step 5: Run the tests and verify they pass**

```bash
npx vitest run src/features/player/player-context.test.ts
```
Expected: PASS, 8 tests.

- [ ] **Step 6: Commit**

```bash
git add Frontend/src/features/player/player-context.tsx Frontend/src/features/player/player-context.test.ts
git commit -m "refactor(player): reducer keeps intent; progress is reported, not simulated"
```

---

### Task 5: Drive playback from a real `<audio>` element

**Files:**
- Modify: `Frontend/src/features/player/player-context.tsx`

**Interfaces:**
- Consumes: `trackStreamQuery` (Task 3); `reducer`, `SET_PROGRESS`, `SET_DURATION`, `ENDED`, `STREAM_ERROR` (Task 4).
- Produces: `PlayerContextValue` gains `unavailable: boolean` and `duration: number | null`; `previewSeconds` now comes from the server (`number | null`) instead of the deleted constant. Task 6 reads these.

- [ ] **Step 1: Delete the simulated ticker**

Remove this block entirely (around line 208):

```ts
  // Simulated playback ticker.
  useEffect(() => {
    if (!state.isPlaying || !hasCurrent) return
    const id = window.setInterval(() => dispatch({ type: 'TICK', limited: limitedRef.current }), 1000)
    return () => window.clearInterval(id)
  }, [state.isPlaying, hasCurrent])
```

- [ ] **Step 2: Fetch the stream for the current track**

Inside `PlayerProvider`, after `currentTrack` is computed:

```ts
  const streamQuery = useQuery({
    ...trackStreamQuery(currentTrack?.id ?? ''),
    enabled: !!currentTrack,
  })
  const stream = streamQuery.data ?? null
  // A preview stream is one the server clipped — it tells us so via previewSeconds.
  const isPreviewStream = stream?.previewSeconds != null
```

Add the imports:

```ts
import { useQuery } from '@tanstack/react-query'
import { trackStreamQuery } from '../../lib/api/queries/playback'
```

- [ ] **Step 3: Report a failed stream as unavailable**

```ts
  // A 503 MEDIA_UNAVAILABLE (no READY asset) must surface as "unavailable", never as a
  // progress bar moving over silence.
  useEffect(() => {
    if (streamQuery.isError) dispatch({ type: 'STREAM_ERROR' })
  }, [streamQuery.isError])
```

- [ ] **Step 4: Add the audio element and wire its events**

Add the ref near the other hooks:

```ts
  const audioRef = useRef<HTMLAudioElement | null>(null)
```

Load the source whenever the signed URL changes:

```ts
  useEffect(() => {
    const el = audioRef.current
    if (!el || !stream) return
    el.src = stream.audioUrl
    el.load()
  }, [stream?.audioUrl])
```

Mirror play/pause intent onto the element, catching the autoplay rejection:

```ts
  useEffect(() => {
    const el = audioRef.current
    if (!el || !stream) return
    if (state.isPlaying) {
      // play() rejects without a user gesture. If it does, stop claiming to play.
      el.play().catch(() => dispatch({ type: 'PAUSE' }))
    } else {
      el.pause()
    }
  }, [state.isPlaying, stream?.audioUrl])
```

Mirror volume and seeks:

```ts
  useEffect(() => {
    const el = audioRef.current
    if (el) el.volume = state.volume
  }, [state.volume])
```

Render the element at the end of the provider's JSX, above `{children}`:

```tsx
      <audio
        ref={audioRef}
        hidden
        preload="metadata"
        onTimeUpdate={(e) => dispatch({ type: 'SET_PROGRESS', seconds: e.currentTarget.currentTime })}
        onLoadedMetadata={(e) => dispatch({ type: 'SET_DURATION', seconds: e.currentTarget.duration })}
        onEnded={() => dispatch({ type: 'ENDED', preview: isPreviewStream })}
        onError={() => dispatch({ type: 'STREAM_ERROR' })}
      />
```

- [ ] **Step 4b: Recover from a signed URL that expired mid-track**

Signed URLs are time-boxed. If one lapses while a fan is listening, the element errors and —
without this — the track would go "unavailable" mid-song, which is both wrong and alarming.
Refetch once and resume where they were:

```ts
  // Position to restore after a mid-track URL refresh.
  const resumeAtRef = useRef<number | null>(null)

  const handleAudioError = () => {
    const el = audioRef.current
    const expired = stream?.expiresAt != null && Date.parse(stream.expiresAt) <= Date.now()
    if (expired && el && state.isPlaying) {
      // The URL lapsed, not the audio. Remember the spot and get a fresh one.
      resumeAtRef.current = el.currentTime
      void streamQuery.refetch()
      return
    }
    dispatch({ type: 'STREAM_ERROR' })
  }
```

Restore the position once the new source has loaded — extend the `onLoadedMetadata` handler
written in Step 4:

```tsx
        onLoadedMetadata={(e) => {
          dispatch({ type: 'SET_DURATION', seconds: e.currentTarget.duration })
          const resumeAt = resumeAtRef.current
          if (resumeAt != null) {
            e.currentTarget.currentTime = resumeAt
            resumeAtRef.current = null
          }
        }}
```

and point `onError` at the new handler: `onError={handleAudioError}`.

- [ ] **Step 5: Make `seek` move the element, not just the state**

Find the `seek` function in the context value and change it to:

```ts
      seek: (seconds: number) => {
        const el = audioRef.current
        if (el) el.currentTime = seconds
        dispatch({ type: 'SET_PROGRESS', seconds })
      },
```

- [ ] **Step 5b: Derive `isPreview` from the server, not from guessed ownership**

`isPreview` is currently inferred client-side at line 202:

```ts
  const isPreview = !!currentTrack && currentTrack.ownership === 'for-sale' && !isTrackOwned(currentTrack.id)
```

That is a guess, and it is the same guess the QA pass found wrong — `ownership` is unreliable
(issue I-13: tracks the fan does not own are reported as `owned`). The server already told us
the truth by clipping the stream. Replace it with:

```ts
  // The server decides preview vs full and says so by returning previewSeconds. Never infer
  // this from `ownership`, which the catalogue is known to misreport (I-13).
  const isPreview = isPreviewStream
```

Delete `limitedRef` and its two assignments — nothing dispatches `TICK` any more.

- [ ] **Step 6: Expose the new values on the context**

In the `useMemo` that builds `PlayerContextValue`, replace `previewSeconds: PREVIEW_SECONDS` with:

```ts
      previewSeconds: stream?.previewSeconds ?? null,
      unavailable: state.unavailable || streamQuery.isError,
      duration: state.duration,
```

Update the `PlayerContextValue` interface accordingly:

```ts
  /** Length of the signed preview, when the server clipped one. Null for full streams. */
  previewSeconds: number | null
  /** True when the current track has no playable stream. */
  unavailable: boolean
  /** Real duration from the audio element, or null before metadata loads. */
  duration: number | null
```

Add `stream`, `streamQuery.isError` and `isPreviewStream` to the `useMemo` dependency array.

- [ ] **Step 7: Run the frontend gate**

```bash
npx vitest run
npm run build
```
Expected: all tests pass; build exits 0. Fix any `PREVIEW_SECONDS` import errors by removing the import — the constant is gone.

- [ ] **Step 8: Commit**

```bash
git add Frontend/src/features/player/player-context.tsx
git commit -m "feat(player): play real audio from the signed stream URL"
```

---

### Task 6: Player bar tells the truth

**Files:**
- Modify: `Frontend/src/components/layout/player-bar.tsx`

**Interfaces:**
- Consumes: `usePlayer()` → `unavailable`, `duration`, `previewSeconds`, `previewHitLimit` (Tasks 4–5).
- Produces: nothing consumed downstream.

- [ ] **Step 1: Read the file and find the three places to change**

```bash
grep -n "usePlayer()\|duration\|previewSeconds\|PREVIEW_SECONDS\|progress" Frontend/src/components/layout/player-bar.tsx
```

Note the line numbers for: the destructure of `usePlayer()`, the duration used for the scrubber's maximum and its right-hand label, and any `PREVIEW_SECONDS` usage.

- [ ] **Step 2: Prefer the element's real duration**

Add `unavailable` and `duration` to the `usePlayer()` destructure, then define:

```tsx
  // The element's duration is authoritative once metadata loads; catalogue metadata is a
  // fallback and can disagree (and does, for a 30s preview of a 3-minute track).
  const effectiveDuration = duration ?? currentTrack?.duration ?? 0
```

Use `effectiveDuration` everywhere the scrubber maximum and the total-time label are computed.

- [ ] **Step 2b: Fix the seek clamp — `previewSeconds` is now nullable**

Line 51 currently clamps a scrub against the preview length:

```tsx
    seek(isPreview ? Math.min(target, previewSeconds) : target)
```

`previewSeconds` is now `number | null`, and `Math.min(target, null)` evaluates to `0` — a
scrub would jump to the start. Guard it:

```tsx
    seek(previewSeconds != null ? Math.min(target, previewSeconds) : target)
```

This also drops the `isPreview` condition, which is now redundant: a non-null `previewSeconds`
*is* the definition of a preview stream.

- [ ] **Step 3: Show unavailable instead of a moving bar**

Wrap the scrubber and the play control:

```tsx
      {unavailable ? (
        <span className="text-xs text-gray-400 dark:text-gray-500">
          Not available to play right now
        </span>
      ) : (
        /* existing scrubber JSX unchanged */
      )}
```

and disable the play button:

```tsx
        <button
          onClick={togglePlay}
          disabled={unavailable}
          aria-label={isPlaying ? 'Pause' : 'Play'}
          className={cn(/* existing classes */, 'disabled:opacity-40 disabled:cursor-not-allowed')}
        >
```

- [ ] **Step 4: Say why a preview stopped**

Where `previewHitLimit` is already handled, make the copy explicit:

```tsx
      {previewHitLimit && (
        <span className="text-xs font-bold text-[#f6c644]">
          Preview ended — buy this track to hear it all
        </span>
      )}
```

- [ ] **Step 5: Run the frontend gate**

```bash
npx vitest run
npm run build
```
Expected: all tests pass; build exits 0.

- [ ] **Step 6: Commit**

```bash
git add Frontend/src/components/layout/player-bar.tsx
git commit -m "feat(player): show unavailable state and the element's real duration"
```

---

### Task 7: Prove it end to end, by ear

**Files:** none — this is verification. Any temporary fixture is deleted at the end.

**Interfaces:**
- Consumes: everything above.
- Produces: the evidence that this plan worked. Nothing depends on it.

**Why this task exists:** every unit test above can pass while the app is still silent. The spec's acceptance criterion is audible playback plus an honest failure state, and neither is unit-testable — jsdom implements no media playback.

- [ ] **Step 1: Ask the user for audio and for the backend to be running**

Report: "Task 7 — I need (a) `bash backend/scripts/smoke.sh` or `cd backend && ./mvnw quarkus:dev -Dquarkus.http.port=18080` running, and (b) 2–3 audio files ≥90 seconds. Please give me the paths."

Do not proceed without real audio; a synthesised tone would not prove the format decision.

- [ ] **Step 2: Attach one asset to an existing catalogue track, temporarily**

The permanent path is the Studio wizard (Stage C, blocked on I-19). For this verification only, ingest one file against a track the fan app already lists, so it is reachable:

```bash
# from the repo root, with the backend on :18080
TOKEN=$(curl -s -X POST http://127.0.0.1:18080/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"<artist QA account>","password":"<password>"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
```

Then upload via the existing release-track endpoint and note the returned asset, or use the media upload use case directly. Record the exact commands used in the PR description so this is reproducible.

- [ ] **Step 3: Confirm the asset reaches READY and the stream resolves**

```bash
PGPASSWORD='<dev password>' psql -h localhost -p 5432 -U postgres -d beatzmedia \
  -tAc "select id, status, hls_key, preview_key from media_asset order by created_at desc limit 3;"
```
Expected: one row `READY` with `hls_key` ending `/full.m4a` and `preview_key` ending `/preview.m4a`.

```bash
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
  http://127.0.0.1:18080/v1/tracks/<trackId>/stream
```
Expected: `200`, not `503`.

- [ ] **Step 4: Verify the four behaviours in the browser**

With the frontend running and the Vite proxy pointed at `:18080`:

1. **Audio plays.** Press play on the seeded track — sound comes out, and the progress bar tracks it.
2. **Preview cap.** As a fan who does **not** own a for-sale track, playback stops at ~30s and shows "Preview ended — buy this track to hear it all". Confirm `previewSeconds` came from the response, not a constant.
3. **Unavailable is honest.** Play a track with no asset. Expect "Not available to play right now", a disabled play button, and **a progress bar that does not move**. This is the acceptance criterion.
4. **Navigation does not interrupt.** Start playback, move between routes — audio continues.

- [ ] **Step 5: Remove the temporary fixture**

Delete the temporarily-attached asset and its objects so the repo's dev data returns to its prior state. Record what was removed.

- [ ] **Step 6: Ask the user to run the full backend gate one last time**

Report: "Task 7 step 6 — please run `bash backend/scripts/verify.sh && bash backend/scripts/smoke.sh` and paste the result."

- [ ] **Step 7: Commit any fixture-cleanup notes and open the PR**

Use `superpowers:finishing-a-development-branch`. The PR description must state which of the four behaviours in Step 4 were observed, and include the commands from Step 2 so the ingest is reproducible.
