/**
 * Global player store.
 *
 * Owns the playback queue, the currently-playing track, transport state
 * (play/pause, progress, volume) and the queue-drawer open flag. Any component
 * can read state and dispatch actions via the `usePlayer()` hook.
 *
 * Playback is *simulated* today: a 1-second ticker advances progress and
 * auto-advances to the next track. When real audio lands, drive `progress`
 * from an <audio> element's timeupdate event and dispatch the same actions —
 * call sites won't change.
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
import type { Track } from '../../types'
import { defaultQueue } from '../../lib/mock-data'
import { useCollection } from '../collection/collection-context'

export type RepeatMode = 'off' | 'all' | 'one'

/** Seconds of a for-sale track a non-owner can preview before it locks. */
export const PREVIEW_SECONDS = 30

interface PlayerState {
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
}

type PlayerAction =
  | { type: 'PLAY_TRACK'; track: Track }
  | { type: 'PLAY_QUEUE'; tracks: Track[]; startIndex: number }
  | { type: 'TOGGLE_PLAY' }
  | { type: 'PLAY' }
  | { type: 'PAUSE' }
  | { type: 'NEXT' }
  | { type: 'PREV' }
  | { type: 'SEEK'; seconds: number }
  | { type: 'TICK'; limited: boolean }
  | { type: 'SET_VOLUME'; volume: number }
  | { type: 'SET_QUEUE_OPEN'; open: boolean }
  | { type: 'CLEAR_QUEUE' }
  | { type: 'TOGGLE_SHUFFLE' }
  | { type: 'CYCLE_REPEAT' }

const initialState: PlayerState = {
  queue: defaultQueue,
  currentIndex: 0,
  isPlaying: false,
  progress: 0,
  volume: 0.7,
  isQueueOpen: false,
  shuffle: false,
  repeat: 'off',
  previewHitLimit: false,
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

function reducer(state: PlayerState, action: PlayerAction): PlayerState {
  switch (action.type) {
    case 'PLAY_TRACK': {
      // If the track is already in the queue, jump to it; otherwise queue it next.
      const existing = state.queue.findIndex((t) => t.id === action.track.id)
      if (existing !== -1) {
        return { ...state, currentIndex: existing, progress: 0, isPlaying: true, previewHitLimit: false }
      }
      const queue = [...state.queue, action.track]
      return { ...state, queue, currentIndex: queue.length - 1, progress: 0, isPlaying: true, previewHitLimit: false }
    }
    case 'PLAY_QUEUE':
      return {
        ...state,
        queue: action.tracks,
        currentIndex: clampIndex(action.startIndex, action.tracks.length),
        progress: 0,
        isPlaying: action.tracks.length > 0,
        previewHitLimit: false,
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
      const ni = advanceIndex(state)
      if (ni === null) return { ...state, progress: 0, isPlaying: false, previewHitLimit: false }
      return { ...state, currentIndex: ni, progress: 0, previewHitLimit: false }
    }
    case 'PREV': {
      // Restart the track if we're more than 3s in, otherwise go to previous.
      if (state.progress > 3 || state.queue.length === 0) return { ...state, progress: 0, previewHitLimit: false }
      if (state.currentIndex === 0) {
        if (state.repeat === 'all') return { ...state, currentIndex: state.queue.length - 1, progress: 0, previewHitLimit: false }
        return { ...state, progress: 0, previewHitLimit: false }
      }
      return { ...state, currentIndex: state.currentIndex - 1, progress: 0, previewHitLimit: false }
    }
    case 'SEEK':
      return { ...state, progress: Math.max(0, action.seconds), previewHitLimit: false }
    case 'TICK': {
      const current = state.queue[state.currentIndex]
      if (!current) return state
      const next = state.progress + 1
      // For-sale tracks the user doesn't own lock after the preview window.
      if (action.limited && next >= PREVIEW_SECONDS) {
        return { ...state, progress: PREVIEW_SECONDS, isPlaying: false, previewHitLimit: true }
      }
      if (next >= current.duration) {
        // Repeat-one replays the same track; otherwise auto-advance / stop.
        if (state.repeat === 'one') return { ...state, progress: 0 }
        const ni = advanceIndex(state)
        if (ni === null) return { ...state, progress: current.duration, isPlaying: false }
        return { ...state, currentIndex: ni, progress: 0 }
      }
      return { ...state, progress: next }
    }
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
  previewSeconds: number
}

const PlayerContext = createContext<PlayerContextValue | null>(null)

export function PlayerProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(reducer, initialState)
  const { isTrackOwned } = useCollection()

  const currentTrack = state.queue[state.currentIndex]
  const hasCurrent = Boolean(currentTrack)
  const isPreview = !!currentTrack && currentTrack.ownership === 'for-sale' && !isTrackOwned(currentTrack.id)

  // Keep the ticker's preview check fresh without restarting the interval.
  const limitedRef = useRef(isPreview)
  limitedRef.current = isPreview

  // Simulated playback ticker.
  useEffect(() => {
    if (!state.isPlaying || !hasCurrent) return
    const id = window.setInterval(() => dispatch({ type: 'TICK', limited: limitedRef.current }), 1000)
    return () => window.clearInterval(id)
  }, [state.isPlaying, hasCurrent])

  const value = useMemo<PlayerContextValue>(
    () => ({
      ...state,
      currentTrack,
      isPreview,
      previewSeconds: PREVIEW_SECONDS,
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
