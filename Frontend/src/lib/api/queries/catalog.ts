import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import {
  toArtist,
  toTrack,
  toAlbum,
  toAlbumTracks,
  toBrowseCategory,
  toLyricLines,
  toPlaylist,
  type ArtistWire,
  type TrackWire,
  type AlbumWire,
  type BrowseCategoryWire,
  type LyricsWire,
  type PlaylistWire,
} from '../mappers'

interface HomeFeedWire {
  trending: TrackWire[]
  top10: TrackWire[]
  featuredAlbums: AlbumWire[]
  rails: {
    newReleases: AlbumWire[]
    popularArtists: ArtistWire[]
    curatedPlaylists: PlaylistWire[]
  }
}

export function homeQuery() {
  return queryOptions({
    queryKey: ['home'],
    queryFn: async () => {
      const wire = await apiFetch<HomeFeedWire>('/home')
      return {
        trending: wire.trending.map(toTrack),
        top10: wire.top10.map(toTrack),
        featuredAlbums: wire.featuredAlbums.map(toAlbum),
        rails: {
          newReleases: wire.rails.newReleases.map(toAlbum),
          popularArtists: wire.rails.popularArtists.map(toArtist),
          curatedPlaylists: wire.rails.curatedPlaylists.map(toPlaylist),
        },
      }
    },
  })
}

export function browseCategoriesQuery() {
  return queryOptions({
    queryKey: ['browse-categories'],
    queryFn: async () => {
      const wire = await apiFetch<BrowseCategoryWire[]>('/browse-categories')
      return wire.map(toBrowseCategory)
    },
  })
}

export function artistQuery(id: string) {
  return queryOptions({
    queryKey: ['artist', id],
    queryFn: async () => toArtist(await apiFetch<ArtistWire>(`/artists/${id}`)),
  })
}

export function artistTracksQuery(id: string) {
  return queryOptions({
    queryKey: ['artist', id, 'tracks'],
    queryFn: async () => (await apiFetch<TrackWire[]>(`/artists/${id}/tracks`)).map(toTrack),
  })
}

export function artistAlbumsQuery(id: string) {
  return queryOptions({
    queryKey: ['artist', id, 'albums'],
    queryFn: async () => (await apiFetch<AlbumWire[]>(`/artists/${id}/albums`)).map(toAlbum),
  })
}

export function albumQuery(id: string) {
  return queryOptions({
    queryKey: ['album', id],
    queryFn: async () => {
      const wire = await apiFetch<AlbumWire>(`/albums/${id}?tracks=true`)
      return { album: toAlbum(wire), tracks: toAlbumTracks(wire) }
    },
  })
}

/**
 * `GET /v1/playlists/{id}` — an editorial (catalog) playlist. The response carries
 * `trackIds`, not full tracks; resolve them with `resolveQuery({ trackIds })`.
 * User-created playlists live on the collection context, not here.
 */
export function playlistQuery(id: string) {
  return queryOptions({
    queryKey: ['playlist', id],
    queryFn: async () => toPlaylist(await apiFetch<PlaylistWire>(`/playlists/${id}`)),
  })
}

export function trackQuery(id: string) {
  return queryOptions({
    queryKey: ['track', id],
    queryFn: async () => toTrack(await apiFetch<TrackWire>(`/tracks/${id}`)),
  })
}

export function lyricsQuery(id: string) {
  return queryOptions({
    queryKey: ['track', id, 'lyrics'],
    queryFn: async () => toLyricLines(await apiFetch<LyricsWire>(`/tracks/${id}/lyrics`)),
  })
}

interface ResolveRequest {
  trackIds?: string[]
  artistIds?: string[]
  albumIds?: string[]
  playlistIds?: string[]
}

interface ResolveWire {
  tracks: TrackWire[]
  artists: ArtistWire[]
  albums: AlbumWire[]
  playlists: PlaylistWire[]
}

export function resolveQuery(ids: ResolveRequest) {
  return queryOptions({
    queryKey: ['resolve', ids],
    queryFn: async () => {
      const wire = await apiFetch<ResolveWire>('/catalog/resolve', { method: 'POST', body: ids })
      return {
        tracks: wire.tracks.map(toTrack),
        artists: wire.artists.map(toArtist),
        albums: wire.albums.map(toAlbum),
        playlists: wire.playlists.map(toPlaylist),
      }
    },
  })
}
