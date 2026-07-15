/**
 * Collection store — the user's saved/liked/followed items + their own playlists.
 *
 * Backed by TanStack Query against /v1/me/collection. Every mutating action updates
 * the cache optimistically (instant UI), calls the API, rolls back + toasts on error,
 * and invalidates on settle. `useCollection()`'s shape is unchanged except
 * createPlaylist is async (the id is server-generated).
 */

import { createContext, useContext, useEffect, useMemo, useRef, type ReactNode } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import type { UserPlaylist } from '../../types'
import { useAuth } from '../auth/auth-context'
import { useToast } from '../../components/ui/toast-provider'
import {
  COLLECTION_KEY,
  EMPTY_COLLECTION,
  collectionQuery,
  toggleMembership,
  setOwned,
  putPlaylist,
  removePlaylistById,
  apiToggleMembership,
  apiCreatePlaylist,
  apiRenamePlaylist,
  apiAddTrack,
  apiRemovePlaylistTrack,
  apiDeletePlaylist,
  type CollectionData,
  type MembershipKey,
  type MembershipKind,
} from '../../lib/api/queries/collection'

interface CollectionContextValue extends CollectionData {
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
  isTrackOwned: (id: string) => boolean
  markTracksOwned: (ids: string[]) => void
  createPlaylist: (title: string, firstTrackId?: string) => Promise<string>
  deletePlaylist: (id: string) => void
  renamePlaylist: (id: string, title: string) => void
  addTrackToPlaylist: (playlistId: string, trackId: string) => void
  removeTrackFromPlaylist: (playlistId: string, trackId: string) => void
  getUserPlaylist: (id: string) => UserPlaylist | undefined
  isTrackInPlaylist: (playlistId: string, trackId: string) => boolean
}

const CollectionContext = createContext<CollectionContextValue | null>(null)

/** Pairs a membership predicate key with its REST path segment. */
const MEMBERSHIP: Record<MembershipKey, MembershipKind> = {
  likedTracks: 'likes/tracks',
  followedArtists: 'follows/artists',
  followedPlaylists: 'follows/playlists',
  followedShows: 'follows/shows',
  savedAlbums: 'saved/albums',
}

export function CollectionProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth()
  const { toast } = useToast()
  const queryClient = useQueryClient()

  const { data } = useQuery({ ...collectionQuery(), enabled: isAuthenticated })
  const collection = data ?? EMPTY_COLLECTION

  // Drop the collection cache on logout (server is the source of truth). Only on an actual
  // authed→unauthed transition — not on first mount (before auth hydrates isAuthenticated is
  // false), where removeQueries would cancel a loader's in-flight collection fetch.
  const wasAuthed = useRef(isAuthenticated)
  useEffect(() => {
    if (wasAuthed.current && !isAuthenticated) {
      queryClient.removeQueries({ queryKey: COLLECTION_KEY })
    }
    wasAuthed.current = isAuthenticated
  }, [isAuthenticated, queryClient])

  // Generic optimistic-cache helper: apply `transform` immediately, run `call`, roll back on error.
  // (No cancelQueries here: it would surface as a CancelledError in route loaders that
  // ensureQueryData the collection. The onSettled invalidate reconciles with the server instead.)
  async function optimistic(transform: (d: CollectionData) => CollectionData, call: () => Promise<unknown>) {
    const prev = queryClient.getQueryData<CollectionData>(COLLECTION_KEY) ?? EMPTY_COLLECTION
    queryClient.setQueryData<CollectionData>(COLLECTION_KEY, transform(prev))
    try {
      await call()
    } catch {
      queryClient.setQueryData<CollectionData>(COLLECTION_KEY, prev)
      toast('Could not update your library', 'error')
    } finally {
      queryClient.invalidateQueries({ queryKey: COLLECTION_KEY })
    }
  }

  const toggle = (key: MembershipKey, id: string) => {
    const willAdd = !collection[key].includes(id)
    void optimistic(
      (d) => toggleMembership(d, key, id),
      () => apiToggleMembership(MEMBERSHIP[key], id, willAdd),
    )
  }

  const createMutation = useMutation({
    mutationFn: async ({ title, firstTrackId }: { title: string; firstTrackId?: string }) => {
      const created = await apiCreatePlaylist(title)
      if (firstTrackId) return apiAddTrack(created.id, firstTrackId)
      return created
    },
    onSuccess: (playlist) => {
      queryClient.setQueryData<CollectionData>(COLLECTION_KEY, (prev) =>
        putPlaylist(prev ?? EMPTY_COLLECTION, playlist),
      )
      queryClient.invalidateQueries({ queryKey: COLLECTION_KEY })
    },
    onError: () => toast('Could not create the playlist', 'error'),
  })

  const value = useMemo<CollectionContextValue>(
    () => ({
      ...collection,
      toggleLikedTrack: (id) => toggle('likedTracks', id),
      toggleFollowedArtist: (id) => toggle('followedArtists', id),
      toggleFollowedPlaylist: (id) => toggle('followedPlaylists', id),
      toggleFollowedShow: (id) => toggle('followedShows', id),
      toggleSavedAlbum: (id) => toggle('savedAlbums', id),
      isTrackLiked: (id) => collection.likedTracks.includes(id),
      isArtistFollowed: (id) => collection.followedArtists.includes(id),
      isPlaylistFollowed: (id) => collection.followedPlaylists.includes(id),
      isShowFollowed: (id) => collection.followedShows.includes(id),
      isAlbumSaved: (id) => collection.savedAlbums.includes(id),
      isTrackOwned: (id) => collection.ownedTracks.includes(id),
      // Ownership is granted server-side at checkout (a later slice). Here we only reflect it
      // optimistically in the cache; GET /me/collection is the real source.
      markTracksOwned: (ids) =>
        queryClient.setQueryData<CollectionData>(COLLECTION_KEY, (prev) => setOwned(prev ?? EMPTY_COLLECTION, ids)),
      createPlaylist: async (title, firstTrackId) => {
        const playlist = await createMutation.mutateAsync({ title: title.trim() || 'New Playlist', firstTrackId })
        return playlist.id
      },
      deletePlaylist: (id) =>
        void optimistic((d) => removePlaylistById(d, id), () => apiDeletePlaylist(id)),
      renamePlaylist: (id, title) =>
        void optimistic(
          (d) => ({ ...d, userPlaylists: d.userPlaylists.map((p) => (p.id === id ? { ...p, title } : p)) }),
          () => apiRenamePlaylist(id, title),
        ),
      addTrackToPlaylist: (playlistId, trackId) =>
        void optimistic(
          (d) => ({
            ...d,
            userPlaylists: d.userPlaylists.map((p) =>
              p.id === playlistId && !p.trackIds.includes(trackId)
                ? { ...p, trackIds: [...p.trackIds, trackId] }
                : p,
            ),
          }),
          () => apiAddTrack(playlistId, trackId),
        ),
      removeTrackFromPlaylist: (playlistId, trackId) =>
        void optimistic(
          (d) => ({
            ...d,
            userPlaylists: d.userPlaylists.map((p) =>
              p.id === playlistId ? { ...p, trackIds: p.trackIds.filter((t) => t !== trackId) } : p,
            ),
          }),
          () => apiRemovePlaylistTrack(playlistId, trackId),
        ),
      getUserPlaylist: (id) => collection.userPlaylists.find((p) => p.id === id),
      isTrackInPlaylist: (playlistId, trackId) =>
        collection.userPlaylists.find((p) => p.id === playlistId)?.trackIds.includes(trackId) ?? false,
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [collection],
  )

  return <CollectionContext.Provider value={value}>{children}</CollectionContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useCollection(): CollectionContextValue {
  const ctx = useContext(CollectionContext)
  if (!ctx) throw new Error('useCollection must be used within a <CollectionProvider>')
  return ctx
}
