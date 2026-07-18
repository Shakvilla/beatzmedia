/**
 * Release-draft store for the new-release wizard, backed by the WU-CAT-5
 * draft flow. Holds the in-progress release as the creator moves across the
 * wizard's four steps (Details → Tracks → Splits → Review). The server draft
 * is created on leaving Details; tracks are uploaded for real; splits / cover
 * art / other extras stay client-only (not sent) until their backends land.
 */

import {
  createContext, useContext, useMemo, useReducer, useRef, type ReactNode,
} from 'react'
import type { Genre } from '../../types'
import type { ReleaseType } from '../../lib/studio-data'
import {
  apiCreateDraft, apiUploadTrack, apiUpdateRelease, apiSubmitRelease, apiDeleteTrack,
  type CreateDraftInput, type UpdateReleaseInput,
} from '../../lib/api/queries/studio'

/** How a collaborator's share has been confirmed. */
export type SplitConfirmation = 'self' | 'confirmed' | 'pending' | 'auto'

/** One collaborator's royalty share of a track (client-only until WU-CAT-6). */
export interface SplitEntry {
  id: string
  name: string
  email: string
  role: string
  percent: number
  confirmation: SplitConfirmation
}

/** A track staged in the release draft. */
export interface UploadedTrack {
  id: string
  title: string
  duration: number
  status: 'uploading' | 'ready' | 'error'
  progress: number
  src: string
  price: number
  explicit: boolean
}

export interface ReleaseDraft {
  /** Server draft id once created (on leaving Details); null before then. */
  releaseId: string | null
  releaseType: ReleaseType
  title: string
  primaryArtist: string
  featuredArtists: string
  label: string
  releaseDate: string
  genre: Genre | ''
  description: string
  coverImage: string | null
  visibility: 'public' | 'scheduled'
  tracks: UploadedTrack[]
  price: number
  splits: Record<string, SplitEntry[]>
  agreementAccepted: boolean
  presaveGenerated: boolean
}

const initialDraft: ReleaseDraft = {
  releaseId: null,
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
  | { type: 'SET_RELEASE_ID'; id: string }
  | { type: 'ADD_PLACEHOLDER'; track: UploadedTrack }
  | { type: 'REPLACE_TRACK'; tempId: string; track: UploadedTrack }
  | { type: 'MARK_TRACK_ERROR'; id: string }
  | { type: 'UPDATE_TRACK'; id: string; patch: Partial<UploadedTrack> }
  | { type: 'REMOVE_TRACK'; id: string }
  | { type: 'MOVE_TRACK'; id: string; dir: -1 | 1 }
  | { type: 'REORDER_TRACKS'; from: number; to: number }
  | { type: 'SET_ALL_PRICES'; price: number }
  | { type: 'SET_TRACK_SPLITS'; trackId: string; splits: SplitEntry[] }
  | { type: 'APPLY_SPLITS_TO_ALL'; splits: SplitEntry[] }
  | { type: 'RESET' }

function reducer(state: ReleaseDraft, action: DraftAction): ReleaseDraft {
  switch (action.type) {
    case 'SET_FIELD':
      return { ...state, [action.field]: action.value }
    case 'SET_RELEASE_ID':
      return { ...state, releaseId: action.id }
    case 'ADD_PLACEHOLDER':
      return { ...state, tracks: [...state.tracks, action.track] }
    case 'REPLACE_TRACK':
      return {
        ...state,
        tracks: state.tracks.map((t) => (t.id === action.tempId ? action.track : t)),
      }
    case 'MARK_TRACK_ERROR':
      return {
        ...state,
        tracks: state.tracks.map((t) => (t.id === action.id ? { ...t, status: 'error' } : t)),
      }
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
        next[t.id] = action.splits.map((s) => ({ ...s, id: `${t.id}-${s.id}` }))
      }
      return { ...state, splits: next }
    }
    case 'RESET':
      return initialDraft
    default:
      return state
  }
}

/** Map the wizard draft → create-draft body. visibility values match the backend 1:1. */
function toCreateInput(d: ReleaseDraft): CreateDraftInput {
  return {
    title: d.title.trim() || undefined,
    type: d.releaseType,
    genre: d.genre || undefined,
    description: d.description.trim() || undefined,
    visibility: d.visibility,
    scheduledAt:
      d.visibility === 'scheduled' && d.releaseDate ? new Date(d.releaseDate).toISOString() : undefined,
  }
}

/** Map the wizard draft → PATCH metadata (no tracks). */
function toMetaPatch(d: ReleaseDraft): UpdateReleaseInput {
  return {
    title: d.title.trim() || undefined,
    genre: d.genre || undefined,
    description: d.description.trim() || undefined,
    visibility: d.visibility,
    scheduledAt:
      d.visibility === 'scheduled' && d.releaseDate ? new Date(d.releaseDate).toISOString() : undefined,
  }
}

const isServerId = (id: string) => !id.startsWith('tmp-')

interface ReleaseDraftContextValue {
  draft: ReleaseDraft
  setField: <K extends keyof ReleaseDraft>(field: K, value: ReleaseDraft[K]) => void
  updateTrack: (id: string, patch: Partial<UploadedTrack>) => void
  removeTrack: (id: string) => Promise<void>
  moveTrack: (id: string, dir: -1 | 1) => void
  reorderTracks: (from: number, to: number) => void
  setAllPrices: (price: number) => void
  setTrackSplits: (trackId: string, splits: SplitEntry[]) => void
  applySplitsToAll: (splits: SplitEntry[]) => void
  createOrUpdateDraft: () => Promise<void>
  uploadTrack: (file: File) => Promise<void>
  submitRelease: () => Promise<void>
  reset: () => void
}

const ReleaseDraftContext = createContext<ReleaseDraftContextValue | null>(null)

export function ReleaseDraftProvider({ children, initial }: { children: ReactNode; initial?: Partial<ReleaseDraft> }) {
  const [draft, dispatch] = useReducer(reducer, { ...initialDraft, ...initial })

  // Mirror state in a ref so async actions read the latest releaseId/price/tracks
  // even when several run concurrently (e.g. multiple uploads).
  const stateRef = useRef(draft)
  stateRef.current = draft

  // Memoizes an in-flight create so concurrent callers (e.g. multi-file uploads)
  // share one apiCreateDraft instead of each firing its own (double-create race).
  const createInFlight = useRef<Promise<string> | null>(null)

  const value = useMemo<ReleaseDraftContextValue>(() => {
    const ensureDraft = (): Promise<string> => {
      if (stateRef.current.releaseId) return Promise.resolve(stateRef.current.releaseId)
      if (createInFlight.current) return createInFlight.current
      const p = (async () => {
        const id = await apiCreateDraft(toCreateInput(stateRef.current))
        stateRef.current = { ...stateRef.current, releaseId: id }
        dispatch({ type: 'SET_RELEASE_ID', id })
        return id
      })()
      createInFlight.current = p
      // Clear the cache once settled so a later create (after reset) can run again.
      p.finally(() => {
        if (createInFlight.current === p) createInFlight.current = null
      })
      return p
    }

    return {
      draft,
      setField: (field, value) => dispatch({ type: 'SET_FIELD', field, value }),
      updateTrack: (id, patch) => dispatch({ type: 'UPDATE_TRACK', id, patch }),
      moveTrack: (id, dir) => dispatch({ type: 'MOVE_TRACK', id, dir }),
      reorderTracks: (from, to) => dispatch({ type: 'REORDER_TRACKS', from, to }),
      setAllPrices: (price) => dispatch({ type: 'SET_ALL_PRICES', price }),
      setTrackSplits: (trackId, splits) => dispatch({ type: 'SET_TRACK_SPLITS', trackId, splits }),
      applySplitsToAll: (splits) => dispatch({ type: 'APPLY_SPLITS_TO_ALL', splits }),
      reset: () => dispatch({ type: 'RESET' }),

      createOrUpdateDraft: async () => {
        if (!stateRef.current.releaseId) {
          await ensureDraft()
        } else {
          await apiUpdateRelease(stateRef.current.releaseId, toMetaPatch(stateRef.current))
        }
      },

      uploadTrack: async (file) => {
        const tempId = `tmp-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`
        dispatch({
          type: 'ADD_PLACEHOLDER',
          track: {
            id: tempId,
            title: file.name.replace(/\.[^.]+$/, ''),
            duration: 0,
            status: 'uploading',
            progress: 0,
            src: URL.createObjectURL(file),
            price: stateRef.current.price,
            explicit: false,
          },
        })
        try {
          const id = await ensureDraft()
          const server = await apiUploadTrack(id, file)
          dispatch({ type: 'REPLACE_TRACK', tempId, track: { ...server, price: stateRef.current.price } })
        } catch {
          dispatch({ type: 'MARK_TRACK_ERROR', id: tempId })
        }
      },

      removeTrack: async (id) => {
        const rid = stateRef.current.releaseId
        dispatch({ type: 'REMOVE_TRACK', id })
        if (rid && isServerId(id)) {
          try {
            await apiDeleteTrack(rid, id)
          } catch {
            // already removed from the UI; server cleanup can happen via the releases list
          }
        }
      },

      submitRelease: async () => {
        const rid = await ensureDraft()
        const s = stateRef.current
        const tracks = s.tracks
          .filter((t) => isServerId(t.id) && t.status !== 'error')
          .map((t, i) => ({ trackId: t.id, position: i, priceMinor: Math.round(t.price * 100) }))
        await apiUpdateRelease(rid, { ...toMetaPatch(s), tracks })
        await apiSubmitRelease(rid, crypto.randomUUID())
      },
    }
  }, [draft])

  return <ReleaseDraftContext.Provider value={value}>{children}</ReleaseDraftContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useReleaseDraft(): ReleaseDraftContextValue {
  const ctx = useContext(ReleaseDraftContext)
  if (!ctx) throw new Error('useReleaseDraft must be used within a <ReleaseDraftProvider>')
  return ctx
}
