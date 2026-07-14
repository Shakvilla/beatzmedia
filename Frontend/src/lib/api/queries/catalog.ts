import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import {
  toArtist,
  toTrack,
  toAlbum,
  toAlbumTracks,
  toBrowseCategory,
  toLyricLines,
  type ArtistWire,
  type TrackWire,
  type AlbumWire,
  type BrowseCategoryWire,
  type LyricsWire,
} from '../mappers'

interface HomeFeedWire {
  trending: TrackWire[]
  top10: TrackWire[]
  featuredAlbums: AlbumWire[]
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
