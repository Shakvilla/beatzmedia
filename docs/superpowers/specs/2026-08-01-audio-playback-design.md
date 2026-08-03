# Audio Playback — Design

**Date:** 2026-08-01
**Issue:** I-12 (`flows/ISSUES.md`) — "No audio ever plays; the player is a simulated timer"
**Scope:** single-file media delivery (backend `media`) + real playback in the fan player (frontend)
**Branch:** `feat/audio-playback` off `master`

## Goal

Make BeatzClik play music. Today pressing play animates a progress counter next to silence.

## What already exists

Established by reading the source, not assumed. The backend is far more complete than the
symptom suggests:

- **`GET /v1/tracks/{id}/stream`** (`PlaybackResource`) → `StreamUrlResponse { audioUrl,
  previewSeconds, expiresAt }`.
- **INV-3 is enforced server-side.** `GetStreamUrlService` picks `PlaybackMode.FULL` only for
  owners; `MediaApplicationService.issueSignedUrl` → `MediaAsset.resolveDeliveryKey(variant)`
  refuses to return the FULL key unless the caller asked for FULL, and refuses anything unless
  the asset is `READY`.
- **A real media pipeline**: `FfmpegAudioTranscoderAdapter` (ffmpeg is baked into
  `Dockerfile.jvm`), `S3ObjectStoreAdapter` over MinIO, presigned time-boxed URLs via
  `UrlSignerPort`.
- **Playback resolves audio by `OwnerRef("catalog", trackId)`**, so any READY asset owned by a
  catalog track id is streamable.
- Podcasts have an equivalent path (`GET /v1/podcasts/…`, `GetEpisodeStreamUrlService`).

## The two defects

### 1. HLS delivery cannot work at all (backend)

`FfmpegAudioTranscoderAdapter` produces HLS: `playlist.m3u8` plus `segment%03d.ts`, and
`preview.m3u8` for the preview variant. `issueSignedUrl` presigns **one object** — the
playlist.

An HLS playlist references its segments by *relative name*. Those segment requests carry no
signature, and `backend/docker-compose.yml` sets `mc anonymous set none
local/beatz-media-delivery` — the delivery bucket is explicitly private. **Every segment
request would 403.**

So playback fails regardless of the frontend. Consistent with the observed state: `media_asset`
holds 2 rows, **0 READY** (both are failed test uploads), and every `/stream` call returns
`503 MEDIA_UNAVAILABLE`. This path has never run end to end.

### 2. The frontend has no audio (frontend)

`features/player/player-context.tsx:208` is commented `// Simulated playback ticker` — a
`window.setInterval` dispatching `TICK` every 1000ms, incrementing `progress` by 1. There is no
`<audio>` element anywhere in the app and nothing calls `/stream`.

The reducer itself is good: queue, `currentIndex`, shuffle, repeat, auto-advance and a
`previewHitLimit` flag all exist and behave correctly. It is driven by a fake clock, not badly
designed.

`PREVIEW_SECONDS = 30` is hardcoded client-side while the server returns `previewSeconds`
per track — the same fabricated-constant pattern removed elsewhere this cycle.

## Part 1 — Single-file delivery (backend, `media`)

Replace HLS with one object per variant.

| Variant | Object key | ffmpeg |
|---|---|---|
| FULL | `delivery/{id}/full.m4a` | `-c:a aac -b:a 128k` |
| PREVIEW | `delivery/{id}/preview.m4a` | same, plus `-t {previewSeconds}` |

**Why single-file rather than fixing HLS.** The catalogue is 2–4 minute tracks sold outright.
HLS's real benefits — adaptive bitrate and live streaming — buy this product nothing, while
costing a segment-signing scheme *and* an `hls.js` dependency in the browser (Chrome and
Firefox cannot play HLS from a plain `<audio src>`). A single presigned object has no unsigned
sub-requests, so the defect class disappears rather than being worked around.

**Why AAC/M4A rather than MP3.** AAC at 128 kbps is roughly equivalent to MP3 at 192 kbps, so
tracks are about a third smaller. In a market where listeners pay for mobile data, that is a
product benefit, not only a technical one. Support is universal on current browsers and Android
4.1+. MP3 remains a one-line fallback if maximum compatibility is preferred later.

**No schema migration.** `media_asset.hls_key` and `preview_key` are opaque `varchar(255)`
object keys; nothing depends on the extension. With 0 READY assets there is no backfill and
nothing to re-transcode.

**One rename, no DB change.** The Java field `hlsKey` becomes `fullKey`, still mapped with
`@Column(name = "hls_key")` and a comment recording the legacy column name. A field called
`hlsKey` holding an `.m4a` key is a misleading-name trap; the rename is contained to
`MediaAsset`, `MediaAssetEntity`, `MediaAssetMapper`.

**Unchanged:** `resolveDeliveryKey` (READY check and the FULL/PREVIEW split — INV-3),
`presignGet`, the private delivery bucket. Content type moves from
`application/vnd.apple.mpegurl` to `audio/mp4`.

**Deleted:** `transcodeToHls`'s segment handling and `uploadHlsDir`'s multi-object upload.

## Part 2 — Frontend playback

**A single `<audio>` element owned by `PlayerProvider`** — rendered once, hidden, living as long
as the provider so route changes never interrupt playback.

**The element becomes the clock.** The `setInterval` is deleted.

| Element event | Dispatch |
|---|---|
| `timeupdate` | `SET_PROGRESS` with the element's real `currentTime` |
| `loadedmetadata` | real duration (catalogue metadata may disagree) |
| `ended` | advance / repeat / preview-ended |
| `error` | playback-unavailable |

`TICK` (add one second) becomes `SET_PROGRESS` (set to a value). The element owns *time*; the
reducer owns *intent* — what is queued and whether it should be playing. The reducer stays a
pure function and stays unit-testable.

**New query** `Frontend/src/lib/api/queries/playback.ts` → `trackStreamQuery(trackId)` against
`GET /v1/tracks/{id}/stream`, with `retry: false` (a `503 MEDIA_UNAVAILABLE` must not
retry-storm) and a `staleTime` shorter than the signed-URL TTL.

**INV-3 becomes physically enforced, and `PREVIEW_SECONDS` is deleted.** A non-owner is signed a
preview file that *is* 30 seconds, so the cap enforces itself — `ended` fires and there is no
further audio to play. The returned `previewSeconds` is used only to say "preview ended — buy to
hear the rest" instead of stopping silently. The client can no longer overrun the preview
because it never receives more audio.

**Anti-fabrication rule.** If `/stream` fails or the element errors, the UI shows *unavailable*
and disables the play control. It must never animate a progress bar over silence. That
behaviour is what made I-12 dangerous rather than merely missing, and it is the acceptance
criterion this design exists to satisfy.

**Two failure modes handled deliberately:**

- **Autoplay rejection.** `audio.play()` returns a promise that rejects without a user gesture.
  Playback always begins from a click, but the rejection is caught and `isPlaying` reverts —
  otherwise the UI claims to play when it does not.
- **Signed-URL expiry mid-track.** On a media error where `expiresAt` has passed, refetch once
  and resume from the last known `currentTime`.

**Files:** `queries/playback.ts` (new), `features/player/player-context.tsx` (reducer, element),
`components/layout/player-bar.tsx` (unavailable state, real duration).

## Testing

- **Reducer unit tests** for `SET_PROGRESS`, `ended` (advance / repeat-one / preview-ended) and
  the error transition. Pure function, no DOM.
- **Query test** for `trackStreamQuery`: URL, key, and that retry is disabled.
- **Not unit-tested:** the `<audio>` element itself. jsdom does not implement media playback, so
  a test asserting "audio plays" would assert nothing. DOM-touching logic stays thin and is
  covered by manual verification instead.
- **Manual verification (the real gate):** a track audibly plays; a non-owner's playback stops
  at 30s with the buy prompt; a `503` shows unavailable with no moving progress bar.
- **Frontend gate:** `npm run build` (`tsc -b`) and the full `npx vitest run` green.
- **Backend gate:** `verify.sh` + `smoke.sh`, run by the user.

## Sequencing and dependencies

The user chose the Studio-wizard path for ingesting real audio. That creates a dependency chain,
and this spec deliberately covers only the first link:

| Stage | Work | Status |
|---|---|---|
| **A** | Single-file delivery + frontend playback (Parts 1 and 2 above) | **this spec** |
| **B** | I-19 — drafts listed as "pending review"; `Approve` returns 409 | separate spec |
| **C** | Ingest real audio through the Studio wizard | needs A, B, the user's files, and the user's distribution-agreement acceptance |

("Part 1/2" are sections of this spec; "Stage A/B/C" are the delivery chain. Only Stage A is in
scope here.)

Parts 1 and 2 of this spec ship together because Part 1 is what makes Part 2 provable. To verify
before Stage C exists, a media asset is attached to an existing catalogue track **temporarily,
for the test only** — the permanent path is the wizard.

**Needed from the user at Stage C:** 2–3 audio files, ideally ≥90 seconds so the 30-second
preview cap is observable. Any common format; the transcoder normalises.

## Out of scope

- **Podcast episode playback.** Same pattern against `GetEpisodeStreamUrlService`; a natural
  follow-up once this proves out.
- **I-19** and the wizard ingest — Stages B and C in the table above.
- Download/offline, gapless, crossfade, adaptive bitrate.
- The `audioUrl` column on `catalog.track`. It is a legacy static field set "after transcoding"
  and is unused by this design, which goes through `/stream`. Removing it is separate cleanup.
