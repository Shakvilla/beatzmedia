/**
 * Release-draft store for the new-release wizard, backed by the WU-CAT-5
 * draft flow. Holds the in-progress release as the creator moves across the
 * wizard's four steps (Details → Tracks → Splits → Review). The server draft
 * is created on leaving Details; tracks and COVER ART are uploaded for real.
 * Splits and the remaining extras stay client-only until their backends land.
 */

import {
  createContext, useContext, useMemo, useReducer, useRef, type ReactNode,
} from 'react'
import { ApiError } from '../../lib/api/errors'
import type { Genre } from '../../types'
import type { ReleaseType } from '../../lib/studio-data'
import {
  apiCreateDraft, apiUploadTrack, apiUpdateRelease, apiSubmitRelease, apiDeleteTrack,
  apiUploadReleaseCover,
  apiGetReleaseDetail,
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
  /**
   * Why the upload failed, taken from the API error. Client-only. Exists because the UI used to
   * render every `status: 'error'` as "metadata missing", which was true of almost none of them.
   */
  error?: string
  /**
   * The uploaded file was already a lossy format (MP3). Client-side only — derived from the
   * picked `File`, never returned by the server, so it must be carried across `REPLACE_TRACK`
   * the same way `price` is. Drives the wizard's quality warning (ADR-35).
   */
  lossy?: boolean
}

/**
 * Whether a picked file is an already-lossy source, so the delivery rendition would be a second
 * lossy generation. The backend admits WAV, FLAC and MP3 (ADR-35); of those only MP3 is lossy.
 * Matched on MIME type with an extension fallback, since some browsers report an empty `type`.
 */
export function isLossyAudio(file: File): boolean {
  return file.type === 'audio/mpeg' || file.type === 'audio/mp3' || /\.mp3$/i.test(file.name)
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
  /**
   * Whether buyers may download this release's audio. `null` means the artist has not chosen yet
   * and must stay `null` (never defaulted to `false`) until they actively pick — the publish guard
   * blocks submission on `null`, and collapsing it to `false` would silently satisfy that guard
   * with an answer the artist never gave.
   */
  downloadable: boolean | null
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
  downloadable: null,
}

type DraftAction =
  | { type: 'SET_FIELD'; field: keyof ReleaseDraft; value: ReleaseDraft[keyof ReleaseDraft] }
  | { type: 'SET_RELEASE_ID'; id: string }
  | { type: 'ADD_PLACEHOLDER'; track: UploadedTrack }
  | { type: 'REPLACE_TRACK'; tempId: string; track: UploadedTrack }
  | { type: 'MARK_TRACK_ERROR'; id: string; error?: string }
  | { type: 'UPDATE_TRACK'; id: string; patch: Partial<UploadedTrack> }
  | { type: 'REMOVE_TRACK'; id: string }
  | { type: 'MOVE_TRACK'; id: string; dir: -1 | 1 }
  | { type: 'REORDER_TRACKS'; from: number; to: number }
  | { type: 'SET_ALL_PRICES'; price: number }
  | { type: 'SET_TRACK_SPLITS'; trackId: string; splits: SplitEntry[] }
  | { type: 'APPLY_SPLITS_TO_ALL'; splits: SplitEntry[] }
  | { type: 'HYDRATE'; draft: ReleaseDraft }
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
        tracks: state.tracks.map((t) =>
          t.id === action.id ? { ...t, status: 'error', error: action.error } : t,
        ),
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
    case 'HYDRATE':
      return action.draft
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
    // `null` (not chosen yet) is omitted rather than sent — the wire field is a plain optional
    // boolean, and omitting it is exactly what leaves the choice unanswered server-side.
    downloadable: d.downloadable ?? undefined,
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
    // Same rule as toCreateInput: `null` must stay omitted, never coerced to `false`, or a PATCH
    // sent before the artist has chosen would clear/overwrite nothing but still risk one day being
    // read as an explicit "no".
    downloadable: d.downloadable ?? undefined,
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
  /**
   * Load an existing server draft into the wizard so the artist resumes where they left off.
   * Without this, "Continue editing" had nowhere to go but the manage/publish page — the wizard
   * always started from an empty draft, so a half-finished release (no audio yet) looked ready
   * to publish.
   */
  hydrateFromServer: (releaseId: string) => Promise<void>
  uploadTrack: (file: File) => Promise<void>
  /**
   * Remembers the picked cover FILE, not just its preview URL.
   *
   * The wizard only ever held `URL.createObjectURL(file)` — a `blob:` URL that never left the
   * browser — so the "Add cover art before submitting" gate passed with nothing stored server-side.
   * The file is kept here rather than in component state because it is chosen on the Details step
   * and uploaded on submit, two different routes.
   */
  setCoverFile: (file: File) => void
  submitRelease: () => Promise<void>
  reset: () => void
}

const ReleaseDraftContext = createContext<ReleaseDraftContextValue | null>(null)

export function ReleaseDraftProvider({ children, initial }: { children: ReactNode; initial?: Partial<ReleaseDraft> }) {
  const [draft, dispatch] = useReducer(reducer, { ...initialDraft, ...initial })

  // Mirror state in a ref so async actions read the latest releaseId/price/tracks
  // even when several run concurrently (e.g. multiple uploads). Writing the ref
  // during render is deliberate (the React "latest value" ref pattern) — a
  // useEffect would lag the mirror one render behind the async closures that
  // read it, reintroducing the staleness this ref exists to prevent.
  const stateRef = useRef(draft)
  // eslint-disable-next-line react-hooks/refs
  stateRef.current = draft

  // Memoizes an in-flight create so concurrent callers (e.g. multi-file uploads)
  // share one apiCreateDraft instead of each firing its own (double-create race).
  const createInFlight = useRef<Promise<string> | null>(null)
  const coverFileRef = useRef<File | null>(null)

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

    /**
     * Watch a freshly uploaded track until transcoding settles it.
     *
     * Upload and transcode are deliberately decoupled on the server — the POST returns as soon as
     * the bytes are stored, with `status: 'uploading'`, and ffmpeg runs on a worker. There is no
     * push channel, so the client has to ask. Without this the wizard showed "uploading" forever
     * on an upload that had already finished, which is what made the step look hung.
     *
     * Bounded at ~2 minutes: a real transcode of a normal track takes seconds, so anything past
     * that is a stuck job, and saying so beats spinning indefinitely. Only the status/duration are
     * patched, never the whole draft — the artist may be editing the title while this runs.
     */
    const pollUntilReady = async (releaseId: string, trackId: string) => {
      const INTERVAL_MS = 2000
      const MAX_ATTEMPTS = 60
      for (let attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
        await new Promise((r) => setTimeout(r, INTERVAL_MS))
        // The artist navigated away or reset the wizard — stop asking.
        if (stateRef.current.releaseId !== releaseId) return
        try {
          const detail = await apiGetReleaseDetail(releaseId)
          const server = detail.tracks?.find((t) => t.trackId === trackId)
          if (!server) return // track removed while we were waiting
          if (server.status === 'ready') {
            dispatch({
              type: 'UPDATE_TRACK',
              id: trackId,
              patch: { status: 'ready', progress: 100, duration: server.duration },
            })
            return
          }
          if (server.status === 'error') {
            dispatch({
              type: 'MARK_TRACK_ERROR',
              id: trackId,
              error: 'processing failed on the server',
            })
            return
          }
        } catch {
          // A transient failure mid-poll is not a failed upload; keep waiting.
        }
      }
      dispatch({
        type: 'MARK_TRACK_ERROR',
        id: trackId,
        error: 'still processing after 2 minutes — reload to check again',
      })
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

      hydrateFromServer: async (releaseId) => {
        const d = await apiGetReleaseDetail(releaseId)
        const type = (d.type ?? 'single') as ReleaseType
        dispatch({
          type: 'HYDRATE',
          draft: {
            ...initialDraft,
            releaseId: d.id,
            releaseType: type,
            title: d.title ?? '',
            genre: (d.genre ?? '') as ReleaseDraft['genre'],
            description: d.description ?? '',
            visibility: d.visibility === 'public' ? 'public' : 'scheduled',
            // The wizard's date input is a plain yyyy-mm-dd, not an ISO instant.
            releaseDate: d.scheduledAt ? d.scheduledAt.slice(0, 10) : '',
            price: d.price?.amount ?? initialDraft.price,
            downloadable: d.downloadable ?? null,
            tracks: (d.tracks ?? [])
              .slice()
              .sort((a, b) => a.position - b.position)
              .map((t) => ({
                id: t.trackId,
                title: t.title,
                duration: t.duration,
                // A server-side track exists, so it uploaded. Anything not yet READY is still
                // being processed rather than failed — 'error' here would resurrect the old
                // "metadata missing" lie on a perfectly good draft.
                status: t.status === 'ready' ? 'ready' : 'uploading',
                progress: t.status === 'ready' ? 100 : 0,
                src: '',
                price: t.price?.amount ?? 0,
                explicit: false,
              })),
            splits: Object.fromEntries(
              (d.tracks ?? []).map((t) => [
                t.trackId,
                (t.splits ?? []).map((sp) => ({
                  id: sp.id,
                  name: sp.name,
                  email: sp.email,
                  role: sp.role,
                  percent: sp.percent,
                  confirmation: sp.confirmation as SplitEntry['confirmation'],
                })),
              ]),
            ),
          },
        })
      },

      uploadTrack: async (file) => {
        const tempId = `tmp-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`
        const lossy = isLossyAudio(file)
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
            lossy,
          },
        })
        try {
          const id = await ensureDraft()
          const server = await apiUploadTrack(id, file)
          // `lossy` is client-derived and absent from the server payload — carry it across, or
          // the warning vanishes the moment the upload resolves.
          dispatch({
            type: 'REPLACE_TRACK',
            tempId,
            track: { ...server, price: stateRef.current.price, lossy },
          })
          // The upload responds the moment the bytes are stored; transcoding runs asynchronously,
          // so `server.status` is always 'uploading' here. Nothing used to close that loop, which
          // is why a perfectly good upload sat on "uploading" forever while the backend had long
          // since marked it ready. Poll until it settles.
          void pollUntilReady(id, server.id)
        } catch (err) {
          // Surface the server's reason. An unsupported format, an expired session and a 500 are
          // very different problems for the artist, and collapsing them lost that entirely.
          const reason =
            err instanceof ApiError
              ? err.status === 401
                ? 'session expired — sign in again'
                : (err.message || `upload failed (${err.status})`)
              : 'upload failed'
          dispatch({ type: 'MARK_TRACK_ERROR', id: tempId, error: reason })
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

      setCoverFile: (file) => {
        coverFileRef.current = file
        dispatch({ type: 'SET_FIELD', field: 'coverImage', value: URL.createObjectURL(file) })
      },
      submitRelease: async () => {
        const rid = await ensureDraft()
        const s = stateRef.current
        const tracks = s.tracks
          .filter((t) => isServerId(t.id) && t.status !== 'error')
          .map((t, i) => ({ trackId: t.id, position: i, priceMinor: Math.round(t.price * 100) }))
        await apiUpdateRelease(rid, { ...toMetaPatch(s), tracks })
        // Upload the cover BEFORE submitting. It is not caught separately: a release that reaches
        // in_review without art would carry the placeholder image on every one of its tracks, and
        // failing the whole submit lets the artist retry rather than discover it after approval.
        const coverFile = coverFileRef.current
        if (coverFile) {
          await apiUploadReleaseCover(rid, coverFile)
        }
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
