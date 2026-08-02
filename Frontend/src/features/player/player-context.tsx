/**
 * Global player store.
 *
 * Owns the playback queue, the currently-playing track, transport state
 * (play/pause, progress, volume) and the queue-drawer open flag. Any component
 * can read state and dispatch actions via the `usePlayer()` hook.
 *
 * The reducer keeps *intent* only (what's queued, whether it should be playing).
 * *Time* comes from outside: an <audio> element reports position/duration/end/error
 * via SET_PROGRESS / SET_DURATION / ENDED / STREAM_ERROR, and this store just records
 * what it's told — it never guesses or simulates progress itself.
 */

import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useReducer,
  useRef,
  type ReactNode,
} from 'react'
import { useQuery } from '@tanstack/react-query'
import type { Track } from '../../types'
import { defaultQueue } from '../../lib/mock-data'
import { trackStreamQuery } from '../../lib/api/queries/playback'

export type RepeatMode = 'off' | 'all' | 'one'

export interface PlayerState {
  queue: Track[]
  currentIndex: number
  isPlaying: boolean
  /** Playback position of the current track, in seconds. */
  progress: number
  /** 0..1 */
  volume: number
  isQueueOpen: boolean
  shuffle: boolean
  repeat: RepeatMode
  /** True once a preview-limited track has hit its preview cap. */
  previewHitLimit: boolean
  /** True when the current track has no playable stream. Never animate progress in this state. */
  unavailable: boolean
  /** Real duration reported by the audio element once its metadata has loaded; null until then. */
  duration: number | null
}

export type PlayerAction =
  | { type: 'PLAY_TRACK'; track: Track }
  | { type: 'PLAY_QUEUE'; tracks: Track[]; startIndex: number }
  | { type: 'TOGGLE_PLAY' }
  | { type: 'PLAY' }
  | { type: 'PAUSE' }
  | { type: 'NEXT' }
  | { type: 'PREV' }
  | { type: 'SEEK'; seconds: number }
  | { type: 'SET_PROGRESS'; seconds: number }
  | { type: 'SET_DURATION'; seconds: number }
  | { type: 'ENDED'; preview: boolean }
  | { type: 'STREAM_ERROR' }
  | { type: 'SET_VOLUME'; volume: number }
  | { type: 'SET_QUEUE_OPEN'; open: boolean }
  | { type: 'CLEAR_QUEUE' }
  | { type: 'TOGGLE_SHUFFLE' }
  | { type: 'CYCLE_REPEAT' }

export const initialState: PlayerState = {
  queue: defaultQueue,
  currentIndex: 0,
  isPlaying: false,
  progress: 0,
  volume: 0.7,
  isQueueOpen: false,
  shuffle: false,
  repeat: 'off',
  previewHitLimit: false,
  unavailable: false,
  duration: null,
}

function clampIndex(index: number, length: number): number {
  if (length === 0) return 0
  return Math.max(0, Math.min(index, length - 1))
}

/**
 * The index to advance to, honouring shuffle + repeat.
 * Returns null when playback should stop (end of queue, no repeat).
 */
function advanceIndex(state: PlayerState): number | null {
  const { queue, currentIndex, shuffle, repeat } = state
  if (queue.length === 0) return null
  if (queue.length === 1) return repeat === 'all' ? 0 : null
  if (shuffle) {
    let r = currentIndex
    while (r === currentIndex) r = Math.floor(Math.random() * queue.length)
    return r
  }
  if (currentIndex < queue.length - 1) return currentIndex + 1
  return repeat === 'all' ? 0 : null
}

export function reducer(state: PlayerState, action: PlayerAction): PlayerState {
  switch (action.type) {
    case 'PLAY_TRACK': {
      // If the track is already in the queue, jump to it; otherwise queue it next.
      const existing = state.queue.findIndex((t) => t.id === action.track.id)
      if (existing !== -1) {
        return { ...state, currentIndex: existing, progress: 0, isPlaying: true, previewHitLimit: false, unavailable: false, duration: null }
      }
      const queue = [...state.queue, action.track]
      return { ...state, queue, currentIndex: queue.length - 1, progress: 0, isPlaying: true, previewHitLimit: false, unavailable: false, duration: null }
    }
    case 'PLAY_QUEUE':
      return {
        ...state,
        queue: action.tracks,
        currentIndex: clampIndex(action.startIndex, action.tracks.length),
        progress: 0,
        isPlaying: action.tracks.length > 0,
        previewHitLimit: false,
        unavailable: false,
        duration: null,
      }
    case 'TOGGLE_PLAY': {
      // Pressing play after a preview ended restarts the preview from the top.
      if (!state.isPlaying && state.previewHitLimit) {
        return { ...state, isPlaying: true, progress: 0, previewHitLimit: false, unavailable: false }
      }
      const isPlaying = !state.isPlaying
      // An explicit press of play is a retry: it must not stay stuck behind a stale
      // `unavailable` from an earlier failure (I5). The provider pairs this with a refetch.
      return { ...state, isPlaying, ...(isPlaying ? { unavailable: false } : {}) }
    }
    case 'PLAY':
      // Same retry contract as TOGGLE_PLAY above: explicit play clears a stale `unavailable`.
      return {
        ...state,
        isPlaying: true,
        unavailable: false,
        ...(state.previewHitLimit ? { progress: 0, previewHitLimit: false } : {}),
      }
    case 'PAUSE':
      return { ...state, isPlaying: false }
    case 'NEXT': {
      // Manual skip always advances (shuffle/repeat-all aware); repeat-one is ignored here.
      // `unavailable`/`duration` are stream facts about the *current* track, so they're only
      // reset when we actually land on a different track — not just because we stopped or
      // rewound the same one. (Mirrors the rule PREV uses below.)
      const ni = advanceIndex(state)
      if (ni === null) return { ...state, progress: 0, isPlaying: false, previewHitLimit: false }
      return { ...state, currentIndex: ni, progress: 0, previewHitLimit: false, unavailable: false, duration: null }
    }
    case 'PREV': {
      // Restart the track if we're more than 3s in, otherwise go to previous.
      // Same rule as NEXT: only clear unavailable/duration when the track actually changes.
      if (state.progress > 3 || state.queue.length === 0) return { ...state, progress: 0, previewHitLimit: false }
      if (state.currentIndex === 0) {
        if (state.repeat === 'all') return { ...state, currentIndex: state.queue.length - 1, progress: 0, previewHitLimit: false, unavailable: false, duration: null }
        return { ...state, progress: 0, previewHitLimit: false }
      }
      return { ...state, currentIndex: state.currentIndex - 1, progress: 0, previewHitLimit: false, unavailable: false, duration: null }
    }
    case 'SEEK':
      return { ...state, progress: Math.max(0, action.seconds), previewHitLimit: false }

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
      // A new track starts clean: a stream failure on the track that just ended must not
      // poison the next one.
      return { ...state, currentIndex: ni, progress: 0, duration: null, unavailable: false }
    }

    case 'STREAM_ERROR':
      // No playable audio. Stop, and let the UI say so instead of animating over silence.
      return { ...state, isPlaying: false, unavailable: true }

    case 'SET_VOLUME':
      return { ...state, volume: Math.max(0, Math.min(1, action.volume)) }
    case 'SET_QUEUE_OPEN':
      return { ...state, isQueueOpen: action.open }
    case 'CLEAR_QUEUE':
      return { ...state, queue: [], currentIndex: 0, progress: 0, isPlaying: false }
    case 'TOGGLE_SHUFFLE':
      return { ...state, shuffle: !state.shuffle }
    case 'CYCLE_REPEAT':
      return { ...state, repeat: state.repeat === 'off' ? 'all' : state.repeat === 'all' ? 'one' : 'off' }
    default:
      return state
  }
}

interface PlayerContextValue extends PlayerState {
  currentTrack: Track | undefined
  playTrack: (track: Track) => void
  playQueue: (tracks: Track[], startIndex?: number) => void
  togglePlay: () => void
  next: () => void
  prev: () => void
  seek: (seconds: number) => void
  setVolume: (volume: number) => void
  toggleQueue: () => void
  setQueueOpen: (open: boolean) => void
  clearQueue: () => void
  toggleShuffle: () => void
  cycleRepeat: () => void
  /** Current track is preview-limited — determined server-side by a clipped stream, never
   *  inferred from `ownership` (which the catalogue can misreport, see I-13). */
  isPreview: boolean
  /** Length of the signed preview, when the server clipped one. Null for full streams. */
  previewSeconds: number | null
}

export type AudioErrorRecovery = 'recover' | 'fail'

/**
 * Decide whether an `<audio>` element error is a lapsed signed URL we can silently recover
 * from (refetch a fresh one and resume) or a genuine playback failure that must surface as
 * "unavailable" instead. Pure and DOM-free on purpose: this is the highest-risk branch in the
 * file (it's what stands between "URL merely expired" and "never animate over silence"), so it
 * needs to be testable without an audio element.
 *
 * - `alreadyRecovered` bounds the loop under clock skew or a persistently broken asset: once one
 *   recovery attempt has been made for the current track without a clean `loadedmetadata` in
 *   between, further errors fail outright instead of refetch-looping forever.
 * - `isPlaying` gates recovery to the mid-track case. An expiry error while paused has no
 *   listener waiting on it — but it must still be recoverable via an explicit retry, not stuck
 *   forever (see the `unavailable`-clearing retry path on TOGGLE_PLAY/PLAY).
 */
export function decideAudioErrorRecovery(
  expiresAt: string | null,
  now: number,
  isPlaying: boolean,
  alreadyRecovered: boolean,
): AudioErrorRecovery {
  if (alreadyRecovered) return 'fail'
  if (!isPlaying) return 'fail'
  if (expiresAt == null) return 'fail'
  if (Date.parse(expiresAt) > now) return 'fail'
  return 'recover'
}

const PlayerContext = createContext<PlayerContextValue | null>(null)

export function PlayerProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(reducer, initialState)

  const currentTrack = state.queue[state.currentIndex]

  const streamQuery = useQuery({
    ...trackStreamQuery(currentTrack?.id ?? ''),
    enabled: !!currentTrack,
  })
  const stream = streamQuery.data ?? null
  // A preview stream is one the server clipped — it tells us so via previewSeconds.
  const isPreviewStream = stream?.previewSeconds != null

  // The server decides preview vs full and says so by returning previewSeconds. Never infer
  // this from `ownership`, which the catalogue is known to misreport (I-13).
  const isPreview = isPreviewStream

  // A 503 MEDIA_UNAVAILABLE (no READY asset) must surface as "unavailable", never as a
  // progress bar moving over silence. `currentTrack?.id` is also a dep because TanStack keeps
  // `isError: true` across a query-key switch when the new key resolves from cache (e.g. B
  // errors, fan plays C, fan returns to B within gcTime) — without it, a same-value
  // true→true transition across that round trip would never re-run this effect (I2).
  // `errorUpdatedAt` covers the retry path itself (I5): `refetch()` on an already-errored
  // query keeps `status: 'error'` throughout, so a refetch that fails AGAIN is an
  // isError true→true transition too — without this, pressing play on a track that is still
  // broken would clear `unavailable`, flip `isPlaying` true, and then never learn the retry
  // failed, landing on the exact "unavailable: true, isPlaying: true" contradiction I5 exists
  // to prevent. `errorUpdatedAt` changes on every settled failure, including a repeat one.
  useEffect(() => {
    if (streamQuery.isError) dispatch({ type: 'STREAM_ERROR' })
  }, [streamQuery.isError, currentTrack?.id, streamQuery.errorUpdatedAt])

  const audioRef = useRef<HTMLAudioElement | null>(null)
  // Which track's audio is currently loaded into the element — lets the load effect tell
  // "different track" (must restart at 0) apart from "same track, freshly signed URL" (must
  // not restart).
  const loadedTrackIdRef = useRef<string | null>(null)
  // Position to restore after a same-track src swap (focus refetch, background staleTime:0
  // refetch, or the expiry recovery below).
  const resumeAtRef = useRef<number | null>(null)
  // Single-shot guard on the expiry-recovery refetch (see decideAudioErrorRecovery / I3):
  // bounds it to one attempt per track between successful loads, so clock skew or a
  // persistently broken asset can't turn it into an infinite error→refetch→error loop.
  const recoveredRef = useRef(false)

  // A track change invalidates any in-flight resume position and re-arms the recovery guard
  // for the new track (I1, I3).
  useEffect(() => {
    resumeAtRef.current = null
    recoveredRef.current = false
  }, [currentTrack?.id])

  // Load the source whenever the signed URL changes. Keyed on the track id, not the URL
  // string: a re-signed URL for the SAME track (focus refetch, background refetch, expiry
  // recovery) must resume in place, not restart from 0 (C2). No stream at all — no current
  // track, or the fetch for a new track hasn't resolved yet — must stop and detach rather than
  // leave a previous track's audio playing under the new track's UI (C1).
  useEffect(() => {
    const el = audioRef.current
    if (!el) return
    if (!stream) {
      el.pause()
      el.removeAttribute('src')
      el.load()
      loadedTrackIdRef.current = null
      return
    }
    // A refetch that lands a byte-identical URL (e.g. refetchOnReconnect, still default-on)
    // must not force a rebuffer gap for nothing — only actually reload when the source changed.
    if (el.src === stream.audioUrl) return
    const sameTrack = loadedTrackIdRef.current === currentTrack?.id
    resumeAtRef.current = sameTrack ? el.currentTime : null
    el.src = stream.audioUrl
    el.load()
    loadedTrackIdRef.current = currentTrack?.id ?? null
  }, [stream, currentTrack?.id])

  // Mirror play/pause intent onto the element. `el.pause()` runs unconditionally when there's
  // nothing to play (no stream yet, or intent is paused) — it must NOT be gated behind
  // `if (!stream) return`, or a track switch to a track with no stream leaves the previous
  // track's audio playing under the new track's "unavailable" UI (C1).
  useEffect(() => {
    const el = audioRef.current
    if (!el) return
    if (state.isPlaying && stream) {
      // play() rejects both for a genuine autoplay block and when a subsequent load()
      // interrupts this call (AbortError). Only the former means "stop claiming to play" —
      // an aborted play is about to be superseded by a new load, not a failure to react to.
      el.play().catch((err: unknown) => {
        if (err instanceof DOMException && err.name === 'AbortError') return
        dispatch({ type: 'PAUSE' })
      })
    } else {
      el.pause()
    }
  }, [state.isPlaying, stream])

  // Mirror volume.
  useEffect(() => {
    const el = audioRef.current
    if (el) el.volume = state.volume
  }, [state.volume])

  const handleAudioError = () => {
    const el = audioRef.current
    const decision = decideAudioErrorRecovery(
      stream?.expiresAt ?? null,
      Date.now(),
      state.isPlaying,
      recoveredRef.current,
    )
    if (decision === 'recover' && el) {
      // The URL lapsed, not the audio. Remember the spot, arm the single-shot guard, and get
      // a fresh one.
      recoveredRef.current = true
      resumeAtRef.current = el.currentTime
      void streamQuery.refetch()
      return
    }
    dispatch({ type: 'STREAM_ERROR' })
  }

  const value = useMemo<PlayerContextValue>(
    () => ({
      ...state,
      currentTrack,
      isPreview,
      previewSeconds: stream?.previewSeconds ?? null,
      unavailable: state.unavailable || streamQuery.isError,
      duration: state.duration,
      playTrack: (track) => dispatch({ type: 'PLAY_TRACK', track }),
      playQueue: (tracks, startIndex = 0) =>
        dispatch({ type: 'PLAY_QUEUE', tracks, startIndex }),
      togglePlay: () => {
        // An explicit press of play is a retry path: if we're stuck behind a stale
        // `unavailable` (including one from a paused expiry error that decideAudioErrorRecovery
        // declined to auto-recover, I5), re-fetch the stream instead of staying stuck forever.
        if (!state.isPlaying && state.unavailable) void streamQuery.refetch()
        dispatch({ type: 'TOGGLE_PLAY' })
      },
      next: () => dispatch({ type: 'NEXT' }),
      prev: () => dispatch({ type: 'PREV' }),
      seek: (seconds: number) => {
        const el = audioRef.current
        if (el) el.currentTime = seconds
        dispatch({ type: 'SET_PROGRESS', seconds })
      },
      setVolume: (volume) => dispatch({ type: 'SET_VOLUME', volume }),
      toggleQueue: () => dispatch({ type: 'SET_QUEUE_OPEN', open: !state.isQueueOpen }),
      setQueueOpen: (open) => dispatch({ type: 'SET_QUEUE_OPEN', open }),
      clearQueue: () => dispatch({ type: 'CLEAR_QUEUE' }),
      toggleShuffle: () => dispatch({ type: 'TOGGLE_SHUFFLE' }),
      cycleRepeat: () => dispatch({ type: 'CYCLE_REPEAT' }),
    }),
    // Both `.isError` (read for `unavailable`) and `.refetch` (called from `togglePlay`) are
    // accessed from `streamQuery` here; eslint-plugin-react-hooks wants the parent object as
    // the dependency in that shape rather than the two member expressions separately, so this
    // depends on `streamQuery` as a whole. (Its identity isn't stable across every render in
    // v5, so this doesn't buy referential memoization — the point is declaring the data
    // dependency correctly, not eliminating recomputation.)
    [state, currentTrack, isPreview, stream, streamQuery],
  )

  return (
    <PlayerContext.Provider value={value}>
      <audio
        ref={audioRef}
        hidden
        preload="metadata"
        onTimeUpdate={(e) => dispatch({ type: 'SET_PROGRESS', seconds: e.currentTarget.currentTime })}
        onLoadedMetadata={(e) => {
          const duration = e.currentTarget.duration
          // A duration of Infinity/NaN (unresolved metadata, some live/streamed formats) is not
          // a real duration — don't let the UI render a bogus scrub bar length from it.
          if (Number.isFinite(duration)) dispatch({ type: 'SET_DURATION', seconds: duration })
          // A clean load clears the single-shot recovery guard so a later, unrelated failure
          // still gets one recovery attempt of its own (I3).
          recoveredRef.current = false
          const resumeAt = resumeAtRef.current
          if (resumeAt != null) {
            e.currentTarget.currentTime = resumeAt
            resumeAtRef.current = null
          }
        }}
        onEnded={() => dispatch({ type: 'ENDED', preview: isPreviewStream })}
        onError={handleAudioError}
      />
      {children}
    </PlayerContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function usePlayer(): PlayerContextValue {
  const ctx = useContext(PlayerContext)
  if (!ctx) throw new Error('usePlayer must be used within a <PlayerProvider>')
  return ctx
}
