import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import {
  toArtist,
  toTrack,
  toAlbum,
  toPlaylist,
  type ArtistWire,
  type TrackWire,
  type AlbumWire,
  type PlaylistWire,
} from '../mappers'
import type { Track, Artist, Album, Playlist } from '../../../types'

export interface TopResultWire {
  entityType: string
  entityId: string
  title: string
  subtitle: string
  payload: Record<string, unknown>
}

interface SearchResultsWire {
  tracks: TrackWire[]
  artists: ArtistWire[]
  albums: AlbumWire[]
  playlists: PlaylistWire[]
  topResult: TopResultWire | null
}

export type SearchTopResult =
  | { kind: 'track'; entity: Track }
  | { kind: 'artist'; entity: Artist }
  | { kind: 'album'; entity: Album }
  | { kind: 'playlist'; entity: Playlist }
  | undefined

export interface SearchResultsData {
  tracks: Track[]
  artists: Artist[]
  albums: Album[]
  playlists: Playlist[]
  topResult: SearchTopResult
}

/**
 * The backend's TopResultView.payload is a loosely-typed map and its entityType can include kinds
 * this endpoint doesn't hydrate (STORE_ITEM/PODCAST/EVENT). Resolving by id-lookup into the already-
 * mapped arrays sidesteps parsing payload and degrades safely (omit, don't crash) for those kinds.
 */
export function resolveTopResult(
  wire: TopResultWire | null,
  tracks: Track[],
  artists: Artist[],
  albums: Album[],
  playlists: Playlist[],
): SearchTopResult {
  if (!wire) return undefined
  switch (wire.entityType) {
    case 'TRACK': {
      const entity = tracks.find((t) => t.id === wire.entityId)
      return entity ? { kind: 'track', entity } : undefined
    }
    case 'ARTIST': {
      const entity = artists.find((a) => a.id === wire.entityId)
      return entity ? { kind: 'artist', entity } : undefined
    }
    case 'ALBUM': {
      const entity = albums.find((a) => a.id === wire.entityId)
      return entity ? { kind: 'album', entity } : undefined
    }
    case 'PLAYLIST': {
      const entity = playlists.find((p) => p.id === wire.entityId)
      return entity ? { kind: 'playlist', entity } : undefined
    }
    default:
      return undefined
  }
}

export function searchQuery(q: string) {
  return queryOptions({
    queryKey: ['search', q],
    queryFn: async () => {
      const wire = await apiFetch<SearchResultsWire>(`/search?q=${encodeURIComponent(q)}`)
      const tracks = wire.tracks.map(toTrack)
      const artists = wire.artists.map(toArtist)
      const albums = wire.albums.map(toAlbum)
      const playlists = wire.playlists.map(toPlaylist)
      return {
        tracks,
        artists,
        albums,
        playlists,
        topResult: resolveTopResult(wire.topResult, tracks, artists, albums, playlists),
      }
    },
  })
}
