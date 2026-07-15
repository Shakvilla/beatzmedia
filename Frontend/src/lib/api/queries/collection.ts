import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import type { UserPlaylist } from '../../../types'

export const COLLECTION_KEY = ['collection'] as const

export type MembershipKey =
  | 'likedTracks'
  | 'followedArtists'
  | 'followedPlaylists'
  | 'followedShows'
  | 'savedAlbums'

export interface CollectionData {
  likedTracks: string[]
  followedArtists: string[]
  followedPlaylists: string[]
  followedShows: string[]
  savedAlbums: string[]
  ownedTracks: string[]
  userPlaylists: UserPlaylist[]
}

export const EMPTY_COLLECTION: CollectionData = {
  likedTracks: [],
  followedArtists: [],
  followedPlaylists: [],
  followedShows: [],
  savedAlbums: [],
  ownedTracks: [],
  userPlaylists: [],
}

interface UserPlaylistWire {
  id: string
  title: string
  description: string | null
  trackIds: string[]
  createdAt: string
}

interface CollectionWire {
  likedTracks: string[]
  followedArtists: string[]
  followedPlaylists: string[]
  followedShows: string[]
  savedAlbums: string[]
  ownedTracks: string[]
  userPlaylists: UserPlaylistWire[]
}

function toPlaylist(w: UserPlaylistWire): UserPlaylist {
  return {
    id: w.id,
    title: w.title,
    description: w.description ?? undefined,
    trackIds: w.trackIds,
    createdAt: w.createdAt,
  }
}

function toCollectionData(w: CollectionWire): CollectionData {
  return {
    likedTracks: w.likedTracks,
    followedArtists: w.followedArtists,
    followedPlaylists: w.followedPlaylists,
    followedShows: w.followedShows,
    savedAlbums: w.savedAlbums,
    ownedTracks: w.ownedTracks,
    userPlaylists: w.userPlaylists.map(toPlaylist),
  }
}

export function collectionQuery() {
  return queryOptions({
    queryKey: COLLECTION_KEY,
    queryFn: async () => toCollectionData(await apiFetch<CollectionWire>('/me/collection')),
  })
}

// ---- pure optimistic transforms (never mutate the input) ----

export function toggleMembership(data: CollectionData, key: MembershipKey, id: string): CollectionData {
  const list = data[key]
  const next = list.includes(id) ? list.filter((x) => x !== id) : [id, ...list]
  return { ...data, [key]: next }
}

export function setOwned(data: CollectionData, ids: string[]): CollectionData {
  return { ...data, ownedTracks: Array.from(new Set([...data.ownedTracks, ...ids])) }
}

export function putPlaylist(data: CollectionData, pl: UserPlaylist): CollectionData {
  return { ...data, userPlaylists: [pl, ...data.userPlaylists.filter((p) => p.id !== pl.id)] }
}

export function removePlaylistById(data: CollectionData, id: string): CollectionData {
  return { ...data, userPlaylists: data.userPlaylists.filter((p) => p.id !== id) }
}

export function renamePlaylistById(data: CollectionData, id: string, title: string): CollectionData {
  return {
    ...data,
    userPlaylists: data.userPlaylists.map((p) => (p.id === id ? { ...p, title } : p)),
  }
}

// ---- raw API calls ----

export type MembershipKind =
  | 'likes/tracks'
  | 'follows/artists'
  | 'follows/playlists'
  | 'follows/shows'
  | 'saved/albums'

export async function apiToggleMembership(kind: MembershipKind, id: string, add: boolean): Promise<void> {
  await apiFetch(`/me/${kind}/${id}`, { method: add ? 'PUT' : 'DELETE' })
}

export async function apiCreatePlaylist(title: string): Promise<UserPlaylist> {
  return toPlaylist(await apiFetch<UserPlaylistWire>('/me/playlists', { method: 'POST', body: { title } }))
}

export async function apiRenamePlaylist(id: string, title: string): Promise<UserPlaylist> {
  return toPlaylist(await apiFetch<UserPlaylistWire>(`/me/playlists/${id}`, { method: 'PATCH', body: { title } }))
}

export async function apiDeletePlaylist(id: string): Promise<void> {
  await apiFetch(`/me/playlists/${id}`, { method: 'DELETE' })
}

export async function apiAddTrack(playlistId: string, trackId: string): Promise<UserPlaylist> {
  return toPlaylist(await apiFetch<UserPlaylistWire>(`/me/playlists/${playlistId}/tracks/${trackId}`, { method: 'PUT' }))
}

export async function apiRemovePlaylistTrack(playlistId: string, trackId: string): Promise<UserPlaylist> {
  return toPlaylist(await apiFetch<UserPlaylistWire>(`/me/playlists/${playlistId}/tracks/${trackId}`, { method: 'DELETE' }))
}
