/**
 * Collection store — the user's saved/liked/followed items + their own playlists.
 *
 * Unlike ownership (which comes from purchases), this is the user curating their
 * own library: liked songs, followed artists/playlists/shows, saved albums, and
 * playlists they create. Buttons across the app write here and Library reads it,
 * so choices persist across navigation. Seeded so Library isn't empty on load.
 */

import { createContext, useContext, useEffect, useMemo, useReducer, useRef, type ReactNode } from 'react'
import type { UserPlaylist } from '../../types'

const PERSIST_KEY = 'beatzclik-collection'

interface CollectionState {
  likedTracks: string[]
  followedArtists: string[]
  followedPlaylists: string[]
  followedShows: string[]
  savedAlbums: string[]
  /** Track ids the user has purchased (unlocks full playback of for-sale tracks). */
  ownedTracks: string[]
  userPlaylists: UserPlaylist[]
}

const INITIAL: CollectionState = {
  likedTracks: ['last-last', 'soja', 'akwasidae', 'friday-night', 'hold-on', 'calm-down'],
  followedArtists: ['black-sherif', 'burna-boy'],
  followedPlaylists: ['vibes-from-the-233', 'made-in-ghana'],
  followedShows: ['sincerely-accra'],
  savedAlbums: ['iron-boy'],
  ownedTracks: [],
  userPlaylists: [
    {
      id: 'up-my-vibes',
      title: 'My Vibes',
      trackIds: ['last-last', 'soja', 'friday-night'],
      createdAt: '2026-06-01',
    },
  ],
}

type ToggleKey = 'likedTracks' | 'followedArtists' | 'followedPlaylists' | 'followedShows' | 'savedAlbums'

type Action =
  | { type: 'TOGGLE'; key: ToggleKey; id: string }
  | { type: 'MARK_OWNED'; ids: string[] }
  | { type: 'CREATE_PLAYLIST'; playlist: UserPlaylist }
  | { type: 'DELETE_PLAYLIST'; id: string }
  | { type: 'RENAME_PLAYLIST'; id: string; title: string }
  | { type: 'ADD_TRACK'; playlistId: string; trackId: string }
  | { type: 'REMOVE_TRACK'; playlistId: string; trackId: string }

function reducer(state: CollectionState, action: Action): CollectionState {
  switch (action.type) {
    case 'TOGGLE': {
      const list = state[action.key]
      const next = list.includes(action.id) ? list.filter((x) => x !== action.id) : [action.id, ...list]
      return { ...state, [action.key]: next }
    }
    case 'MARK_OWNED':
      return { ...state, ownedTracks: Array.from(new Set([...state.ownedTracks, ...action.ids])) }
    case 'CREATE_PLAYLIST':
      return { ...state, userPlaylists: [action.playlist, ...state.userPlaylists] }
    case 'DELETE_PLAYLIST':
      return { ...state, userPlaylists: state.userPlaylists.filter((p) => p.id !== action.id) }
    case 'RENAME_PLAYLIST':
      return {
        ...state,
        userPlaylists: state.userPlaylists.map((p) => (p.id === action.id ? { ...p, title: action.title } : p)),
      }
    case 'ADD_TRACK':
      return {
        ...state,
        userPlaylists: state.userPlaylists.map((p) =>
          p.id === action.playlistId && !p.trackIds.includes(action.trackId)
            ? { ...p, trackIds: [...p.trackIds, action.trackId] }
            : p,
        ),
      }
    case 'REMOVE_TRACK':
      return {
        ...state,
        userPlaylists: state.userPlaylists.map((p) =>
          p.id === action.playlistId ? { ...p, trackIds: p.trackIds.filter((t) => t !== action.trackId) } : p,
        ),
      }
    default:
      return state
  }
}

interface CollectionContextValue extends CollectionState {
  toggleLikedTrack: (id: string) => void
  toggleFollowedArtist: (id: string) => void
  toggleFollowedPlaylist: (id: string) => void
  toggleFollowedShow: (id: string) => void
  toggleSavedAlbum: (id: string) => void
  isTrackLiked: (id: string) => boolean
  isArtistFollowed: (id: string) => boolean
  isPlaylistFollowed: (id: string) => boolean
  isShowFollowed: (id: string) => boolean
  isAlbumSaved: (id: string) => boolean
  // ownership
  isTrackOwned: (id: string) => boolean
  markTracksOwned: (ids: string[]) => void
  // user playlists
  createPlaylist: (title: string, firstTrackId?: string) => string
  deletePlaylist: (id: string) => void
  renamePlaylist: (id: string, title: string) => void
  addTrackToPlaylist: (playlistId: string, trackId: string) => void
  removeTrackFromPlaylist: (playlistId: string, trackId: string) => void
  getUserPlaylist: (id: string) => UserPlaylist | undefined
  isTrackInPlaylist: (playlistId: string, trackId: string) => boolean
}

const CollectionContext = createContext<CollectionContextValue | null>(null)

function genPlaylistId(title: string): string {
  const slug = title.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '').slice(0, 24) || 'playlist'
  return `up-${slug}-${Date.now().toString(36)}`
}

/** Hydrate from localStorage, backfilling any keys added since the save. */
function load(): CollectionState {
  try {
    const raw = typeof window !== 'undefined' ? localStorage.getItem(PERSIST_KEY) : null
    return raw ? { ...INITIAL, ...(JSON.parse(raw) as Partial<CollectionState>) } : INITIAL
  } catch {
    return INITIAL
  }
}

export function CollectionProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(reducer, undefined, load)
  const first = useRef(true)

  useEffect(() => {
    if (first.current) { first.current = false; return }
    try { localStorage.setItem(PERSIST_KEY, JSON.stringify(state)) } catch { /* ignore */ }
  }, [state])

  const value = useMemo<CollectionContextValue>(
    () => ({
      ...state,
      toggleLikedTrack: (id) => dispatch({ type: 'TOGGLE', key: 'likedTracks', id }),
      toggleFollowedArtist: (id) => dispatch({ type: 'TOGGLE', key: 'followedArtists', id }),
      toggleFollowedPlaylist: (id) => dispatch({ type: 'TOGGLE', key: 'followedPlaylists', id }),
      toggleFollowedShow: (id) => dispatch({ type: 'TOGGLE', key: 'followedShows', id }),
      toggleSavedAlbum: (id) => dispatch({ type: 'TOGGLE', key: 'savedAlbums', id }),
      isTrackLiked: (id) => state.likedTracks.includes(id),
      isArtistFollowed: (id) => state.followedArtists.includes(id),
      isPlaylistFollowed: (id) => state.followedPlaylists.includes(id),
      isShowFollowed: (id) => state.followedShows.includes(id),
      isAlbumSaved: (id) => state.savedAlbums.includes(id),
      isTrackOwned: (id) => state.ownedTracks.includes(id),
      markTracksOwned: (ids) => dispatch({ type: 'MARK_OWNED', ids }),
      createPlaylist: (title, firstTrackId) => {
        const id = genPlaylistId(title)
        dispatch({
          type: 'CREATE_PLAYLIST',
          playlist: {
            id,
            title: title.trim() || 'New Playlist',
            trackIds: firstTrackId ? [firstTrackId] : [],
            createdAt: new Date().toISOString(),
          },
        })
        return id
      },
      deletePlaylist: (id) => dispatch({ type: 'DELETE_PLAYLIST', id }),
      renamePlaylist: (id, title) => dispatch({ type: 'RENAME_PLAYLIST', id, title }),
      addTrackToPlaylist: (playlistId, trackId) => dispatch({ type: 'ADD_TRACK', playlistId, trackId }),
      removeTrackFromPlaylist: (playlistId, trackId) => dispatch({ type: 'REMOVE_TRACK', playlistId, trackId }),
      getUserPlaylist: (id) => state.userPlaylists.find((p) => p.id === id),
      isTrackInPlaylist: (playlistId, trackId) =>
        state.userPlaylists.find((p) => p.id === playlistId)?.trackIds.includes(trackId) ?? false,
    }),
    [state],
  )

  return <CollectionContext.Provider value={value}>{children}</CollectionContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useCollection(): CollectionContextValue {
  const ctx = useContext(CollectionContext)
  if (!ctx) throw new Error('useCollection must be used within a <CollectionProvider>')
  return ctx
}
