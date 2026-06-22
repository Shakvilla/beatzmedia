/**
 * Release-draft store for the new-release wizard.
 *
 * Holds the in-progress release as the creator moves across the wizard's four
 * steps (Details → Tracks → Pricing → Review). Scoped to the wizard layout
 * route rather than the whole app — mounted by `studio.release.new.tsx`.
 *
 * Persistence is in-memory for now (resets on refresh). When the API lands,
 * `saveDraft` / `publish` become mutations and the initial state is hydrated
 * from a fetched draft.
 */

import { createContext, useContext, useMemo, useReducer, type ReactNode } from 'react'
import type { Genre } from '../../types'
import type { ReleaseType } from '../../lib/studio-data'

/** How a collaborator's share has been confirmed. */
export type SplitConfirmation = 'self' | 'confirmed' | 'pending' | 'auto'

/** One collaborator's royalty share of a track. */
export interface SplitEntry {
  id: string
  name: string
  /** Email / handle used to send the confirmation request. */
  email: string
  role: string
  /** Percentage of the creator pool (0–100). */
  percent: number
  confirmation: SplitConfirmation
}

/** A track being uploaded / staged in the release draft. */
export interface UploadedTrack {
  id: string
  title: string
  /** Length in seconds. */
  duration: number
  status: 'uploading' | 'ready' | 'error'
  /** Upload progress, 0–100. */
  progress: number
  /** Object URL of the uploaded audio, so the artist can preview it. */
  src: string
  /** Price in cedis; 0 = free. */
  price: number
  /** Flagged as containing explicit content. */
  explicit: boolean
}

export interface ReleaseDraft {
  releaseType: ReleaseType
  title: string
  primaryArtist: string
  featuredArtists: string
  label: string
  releaseDate: string
  genre: Genre | ''
  description: string
  /** Object URL / remote URL of the uploaded cover, if any. */
  coverImage: string | null
  /** 'public' = live immediately, 'scheduled' = goes public on releaseDate. */
  visibility: 'public' | 'scheduled'
  tracks: UploadedTrack[]
  /** Price per track in cedis; 0 = free. */
  price: number
  /** Royalty splits keyed by track id. */
  splits: Record<string, SplitEntry[]>
  /** Distribution agreement accepted on the review step. */
  agreementAccepted: boolean
  /** Whether a pre-save link has been generated for a scheduled release. */
  presaveGenerated: boolean
}

const initialDraft: ReleaseDraft = {
  releaseType: 'single',
  title: '',
  primaryArtist: '',
  featuredArtists: '',
  label: '',
  releaseDate: '',
  genre: '',
  description: '',
  coverImage: null,
  visibility: 'scheduled',
  tracks: [],
  price: 2.5,
  splits: {},
  agreementAccepted: false,
  presaveGenerated: false,
}

type DraftAction =
  | { type: 'SET_FIELD'; field: keyof ReleaseDraft; value: ReleaseDraft[keyof ReleaseDraft] }
  | { type: 'ADD_TRACKS'; tracks: UploadedTrack[] }
  | { type: 'UPDATE_TRACK'; id: string; patch: Partial<UploadedTrack> }
  | { type: 'REMOVE_TRACK'; id: string }
  | { type: 'MOVE_TRACK'; id: string; dir: -1 | 1 }
  | { type: 'REORDER_TRACKS'; from: number; to: number }
  | { type: 'SET_ALL_PRICES'; price: number }
  | { type: 'SET_TRACK_SPLITS'; trackId: string; splits: SplitEntry[] }
  | { type: 'APPLY_SPLITS_TO_ALL'; splits: SplitEntry[] }
  | { type: 'TICK_UPLOADS' }
  | { type: 'RESET' }

function reducer(state: ReleaseDraft, action: DraftAction): ReleaseDraft {
  switch (action.type) {
    case 'SET_FIELD':
      return { ...state, [action.field]: action.value }
    case 'ADD_TRACKS':
      return { ...state, tracks: [...state.tracks, ...action.tracks] }
    case 'UPDATE_TRACK':
      return {
        ...state,
        tracks: state.tracks.map((t) => (t.id === action.id ? { ...t, ...action.patch } : t)),
      }
    case 'REMOVE_TRACK':
      return { ...state, tracks: state.tracks.filter((t) => t.id !== action.id) }
    case 'MOVE_TRACK': {
      const i = state.tracks.findIndex((t) => t.id === action.id)
      const j = i + action.dir
      if (i === -1 || j < 0 || j >= state.tracks.length) return state
      const tracks = [...state.tracks]
      ;[tracks[i], tracks[j]] = [tracks[j], tracks[i]]
      return { ...state, tracks }
    }
    case 'REORDER_TRACKS': {
      const { from, to } = action
      if (from === to || from < 0 || to < 0 || from >= state.tracks.length || to >= state.tracks.length) return state
      const tracks = [...state.tracks]
      const [moved] = tracks.splice(from, 1)
      tracks.splice(to, 0, moved)
      return { ...state, tracks }
    }
    case 'SET_ALL_PRICES':
      return { ...state, tracks: state.tracks.map((t) => ({ ...t, price: action.price })) }
    case 'SET_TRACK_SPLITS':
      return { ...state, splits: { ...state.splits, [action.trackId]: action.splits } }
    case 'APPLY_SPLITS_TO_ALL': {
      const next: Record<string, SplitEntry[]> = {}
      for (const t of state.tracks) {
        // Clone entries with fresh ids so per-track edits stay independent.
        next[t.id] = action.splits.map((s) => ({ ...s, id: `${t.id}-${s.id}` }))
      }
      return { ...state, splits: next }
    }
    case 'TICK_UPLOADS': {
      if (!state.tracks.some((t) => t.status === 'uploading')) return state
      return {
        ...state,
        tracks: state.tracks.map((t) => {
          if (t.status !== 'uploading') return t
          const progress = Math.min(100, t.progress + 8 + Math.floor(Math.random() * 12))
          return progress >= 100 ? { ...t, progress: 100, status: 'ready' } : { ...t, progress }
        }),
      }
    }
    case 'RESET':
      return initialDraft
    default:
      return state
  }
}

interface ReleaseDraftContextValue {
  draft: ReleaseDraft
  setField: <K extends keyof ReleaseDraft>(field: K, value: ReleaseDraft[K]) => void
  addTracks: (tracks: UploadedTrack[]) => void
  updateTrack: (id: string, patch: Partial<UploadedTrack>) => void
  removeTrack: (id: string) => void
  moveTrack: (id: string, dir: -1 | 1) => void
  reorderTracks: (from: number, to: number) => void
  setAllPrices: (price: number) => void
  setTrackSplits: (trackId: string, splits: SplitEntry[]) => void
  applySplitsToAll: (splits: SplitEntry[]) => void
  tickUploads: () => void
  reset: () => void
}

const ReleaseDraftContext = createContext<ReleaseDraftContextValue | null>(null)

export function ReleaseDraftProvider({ children, initial }: { children: ReactNode; initial?: Partial<ReleaseDraft> }) {
  const [draft, dispatch] = useReducer(reducer, { ...initialDraft, ...initial })

  const value = useMemo<ReleaseDraftContextValue>(
    () => ({
      draft,
      setField: (field, value) => dispatch({ type: 'SET_FIELD', field, value }),
      addTracks: (tracks) => dispatch({ type: 'ADD_TRACKS', tracks }),
      updateTrack: (id, patch) => dispatch({ type: 'UPDATE_TRACK', id, patch }),
      removeTrack: (id) => dispatch({ type: 'REMOVE_TRACK', id }),
      moveTrack: (id, dir) => dispatch({ type: 'MOVE_TRACK', id, dir }),
      reorderTracks: (from, to) => dispatch({ type: 'REORDER_TRACKS', from, to }),
      setAllPrices: (price) => dispatch({ type: 'SET_ALL_PRICES', price }),
      setTrackSplits: (trackId, splits) => dispatch({ type: 'SET_TRACK_SPLITS', trackId, splits }),
      applySplitsToAll: (splits) => dispatch({ type: 'APPLY_SPLITS_TO_ALL', splits }),
      tickUploads: () => dispatch({ type: 'TICK_UPLOADS' }),
      reset: () => dispatch({ type: 'RESET' }),
    }),
    [draft],
  )

  return <ReleaseDraftContext.Provider value={value}>{children}</ReleaseDraftContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useReleaseDraft(): ReleaseDraftContextValue {
  const ctx = useContext(ReleaseDraftContext)
  if (!ctx) throw new Error('useReleaseDraft must be used within a <ReleaseDraftProvider>')
  return ctx
}
