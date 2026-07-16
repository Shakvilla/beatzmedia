import { describe, it, expect, vi, beforeEach } from 'vitest'
import { apiFetch } from '../client'
import { searchQuery, resolveTopResult } from './search'
import type { Track, Artist, Album, Playlist } from '../../../types'

vi.mock('../client', () => ({ apiFetch: vi.fn() }))

const ctx = {} as any

const wireTrack = {
  id: 't1', title: 'Second Sermon', artistId: 'a1', artistName: 'Black Sherif', albumId: null,
  albumTitle: null, duration: 200, image: 'track.jpg', ownership: 'for-sale',
  price: { amount: 5, currency: 'GHS' }, plays: 100, audioUrl: null, credits: null, quality: null,
  year: 2022,
}
const wireArtist = {
  id: 'a1', name: 'Black Sherif', image: 'artist.jpg', coverImage: null, verified: true,
  monthlyListeners: 1000, followers: 500, bio: null, location: null, genres: null,
}
const wireAlbum = {
  id: 'al1', title: 'The Villain I Never Was', artistId: 'a1', artistName: 'Black Sherif', year: 2022,
  coverImage: 'album.jpg', genres: null, trackIds: ['t1'], tracks: null,
}
const wirePlaylist = {
  id: 'p1', title: 'Afrobeats Mix', description: null, creator: 'BeatzClik', creatorAvatar: null,
  image: 'playlist.jpg', isPublic: true, followers: null, trackIds: ['t1'], tracks: null,
}

describe('searchQuery', () => {
  beforeEach(() => vi.mocked(apiFetch).mockReset())

  it('fetches /search?q= and maps all four categories, with no top result', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      tracks: [wireTrack], artists: [wireArtist], albums: [wireAlbum], playlists: [wirePlaylist],
      topResult: null,
    })

    const result = await searchQuery('sherif').queryFn!(ctx)

    expect(apiFetch).toHaveBeenCalledWith('/search?q=sherif')
    expect(result.tracks).toHaveLength(1)
    expect(result.artists).toHaveLength(1)
    expect(result.albums).toHaveLength(1)
    expect(result.playlists).toHaveLength(1)
    expect(result.topResult).toBeUndefined()
  })

  it('URL-encodes the query', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ tracks: [], artists: [], albums: [], playlists: [], topResult: null })

    await searchQuery('black sherif').queryFn!(ctx)

    expect(apiFetch).toHaveBeenCalledWith('/search?q=black%20sherif')
  })

  it('resolves a TRACK topResult by id-lookup into the mapped tracks array', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      tracks: [wireTrack], artists: [], albums: [], playlists: [],
      topResult: { entityType: 'TRACK', entityId: 't1', title: 'Second Sermon', subtitle: 'Black Sherif', payload: {} },
    })

    const result = await searchQuery('sherif').queryFn!(ctx)

    expect(result.topResult).toEqual({ kind: 'track', entity: result.tracks[0] })
  })
})

describe('resolveTopResult (pure)', () => {
  const tracks: Track[] = [{
    id: 't1', title: 'Second Sermon', artistId: 'a1', artistName: 'Black Sherif', duration: 200,
    image: 'track.jpg', ownership: 'for-sale', price: { amount: 5, currency: 'GHS' },
  }]
  const artists: Artist[] = [{ id: 'a1', name: 'Black Sherif', image: 'artist.jpg' }]
  const albums: Album[] = [{ id: 'al1', title: 'The Villain I Never Was', artistId: 'a1', artistName: 'Black Sherif', year: 2022, coverImage: 'album.jpg', trackIds: ['t1'] }]
  const playlists: Playlist[] = [{ id: 'p1', title: 'Afrobeats Mix', creator: 'BeatzClik', image: 'playlist.jpg', isPublic: true, trackIds: ['t1'] }]

  it('returns undefined when the wire topResult is null', () => {
    expect(resolveTopResult(null, tracks, artists, albums, playlists)).toBeUndefined()
  })

  it('resolves ARTIST', () => {
    const wire = { entityType: 'ARTIST', entityId: 'a1', title: 'Black Sherif', subtitle: '', payload: {} }
    expect(resolveTopResult(wire, tracks, artists, albums, playlists)).toEqual({ kind: 'artist', entity: artists[0] })
  })

  it('resolves ALBUM', () => {
    const wire = { entityType: 'ALBUM', entityId: 'al1', title: 'The Villain I Never Was', subtitle: '', payload: {} }
    expect(resolveTopResult(wire, tracks, artists, albums, playlists)).toEqual({ kind: 'album', entity: albums[0] })
  })

  it('resolves PLAYLIST', () => {
    const wire = { entityType: 'PLAYLIST', entityId: 'p1', title: 'Afrobeats Mix', subtitle: '', payload: {} }
    expect(resolveTopResult(wire, tracks, artists, albums, playlists)).toEqual({ kind: 'playlist', entity: playlists[0] })
  })

  it('returns undefined for an un-hydrated kind (e.g. STORE_ITEM) rather than crashing', () => {
    const wire = { entityType: 'STORE_ITEM', entityId: 'si1', title: 'Merch', subtitle: '', payload: {} }
    expect(resolveTopResult(wire, tracks, artists, albums, playlists)).toBeUndefined()
  })

  it('returns undefined when the entityId is not found in the matching array', () => {
    const wire = { entityType: 'TRACK', entityId: 'does-not-exist', title: 'Ghost', subtitle: '', payload: {} }
    expect(resolveTopResult(wire, tracks, artists, albums, playlists)).toBeUndefined()
  })
})
