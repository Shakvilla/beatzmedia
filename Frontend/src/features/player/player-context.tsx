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
  useMemo,
  useReducer,
  type ReactNode,
} from 'react'
import type { Track } from '../../types'
import { defaultQueue } from '../../lib/mock-data'
import { useCollection } from '../collection/collection-context'

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
  /** Real duration from the audio element once known; falls back to catalogue metadata. */
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
      if (!state.isPlaying && state.previewHitLimit) return { ...state, isPlaying: true, progress: 0, previewHitLimit: false }
      return { ...state, isPlaying: !state.isPlaying }
    }
    case 'PLAY':
      return { ...state, isPlaying: true, ...(state.previewHitLimit ? { progress: 0, previewHitLimit: false } : {}) }
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
  /** Current track is a for-sale track the user doesn't own (preview-limited). */
  isPreview: boolean
}

const PlayerContext = createContext<PlayerContextValue | null>(null)

export function PlayerProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(reducer, initialState)
  const { isTrackOwned } = useCollection()

  const currentTrack = state.queue[state.currentIndex]
  const isPreview = !!currentTrack && currentTrack.ownership === 'for-sale' && !isTrackOwned(currentTrack.id)

  // Progress/duration/ended/error now come from the <audio> element (Task 5), which
  // dispatches SET_PROGRESS / SET_DURATION / ENDED / STREAM_ERROR. This provider no
  // longer simulates time itself.

  const value = useMemo<PlayerContextValue>(
    () => ({
      ...state,
      currentTrack,
      isPreview,
      playTrack: (track) => dispatch({ type: 'PLAY_TRACK', track }),
      playQueue: (tracks, startIndex = 0) =>
        dispatch({ type: 'PLAY_QUEUE', tracks, startIndex }),
      togglePlay: () => dispatch({ type: 'TOGGLE_PLAY' }),
      next: () => dispatch({ type: 'NEXT' }),
      prev: () => dispatch({ type: 'PREV' }),
      seek: (seconds) => dispatch({ type: 'SEEK', seconds }),
      setVolume: (volume) => dispatch({ type: 'SET_VOLUME', volume }),
      toggleQueue: () => dispatch({ type: 'SET_QUEUE_OPEN', open: !state.isQueueOpen }),
      setQueueOpen: (open) => dispatch({ type: 'SET_QUEUE_OPEN', open }),
      clearQueue: () => dispatch({ type: 'CLEAR_QUEUE' }),
      toggleShuffle: () => dispatch({ type: 'TOGGLE_SHUFFLE' }),
      cycleRepeat: () => dispatch({ type: 'CYCLE_REPEAT' }),
    }),
    [state, currentTrack, isPreview],
  )

  return <PlayerContext.Provider value={value}>{children}</PlayerContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function usePlayer(): PlayerContextValue {
  const ctx = useContext(PlayerContext)
  if (!ctx) throw new Error('usePlayer must be used within a <PlayerProvider>')
  return ctx
}
