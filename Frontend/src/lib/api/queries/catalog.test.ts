import { describe, it, expect, vi, beforeEach } from 'vitest'
import { apiFetch } from '../client'
import { homeQuery, artistQuery, albumQuery, trackQuery, browseCategoriesQuery, lyricsQuery, resolveQuery } from './catalog'

vi.mock('../client', () => ({ apiFetch: vi.fn() }))

const ctx = {} as any

describe('catalog query factories', () => {
  beforeEach(() => vi.mocked(apiFetch).mockReset())

  it('homeQuery fetches /home and maps all three sections', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      trending: [{ id: 't1', title: 'A', artistId: 'a1', artistName: 'Art', albumId: null, albumTitle: null, duration: 10, image: 'i', ownership: 'free', price: null, plays: 1, audioUrl: null, credits: null, quality: null, year: null }],
      top10: [],
      featuredAlbums: [{ id: 'al1', title: 'B', artistId: 'a1', artistName: 'Art', year: 2024, coverImage: 'c', genres: null, trackIds: [], tracks: null }],
    })

    const result = await homeQuery().queryFn!(ctx)

    expect(apiFetch).toHaveBeenCalledWith('/home')
    expect(result.trending).toHaveLength(1)
    expect(result.featuredAlbums).toHaveLength(1)
  })

  it('browseCategoriesQuery fetches /browse-categories', async () => {
    vi.mocked(apiFetch).mockResolvedValue([{ id: 'c1', title: 'Afrobeats', colorClass: 'bg-red-500' }])

    const result = await browseCategoriesQuery().queryFn!(ctx)

    expect(apiFetch).toHaveBeenCalledWith('/browse-categories')
    expect(result).toEqual([{ id: 'c1', title: 'Afrobeats', colorClass: 'bg-red-500' }])
  })

  it('artistQuery fetches /artists/:id', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      id: 'a1', name: 'Black Sherif', image: 'img', coverImage: null, verified: true,
      monthlyListeners: 1, followers: 1, bio: null, location: null, genres: null,
    })

    const result = await artistQuery('a1').queryFn!(ctx)

    expect(apiFetch).toHaveBeenCalledWith('/artists/a1')
    expect(result.name).toBe('Black Sherif')
  })

  it('albumQuery requests embedded tracks and splits the result', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      id: 'al1', title: 'Album', artistId: 'a1', artistName: 'Art', year: 2024, coverImage: 'c',
      genres: null, trackIds: ['t1'],
      tracks: [{ id: 't1', title: 'Song', artistId: 'a1', artistName: 'Art', albumId: 'al1', albumTitle: 'Album', duration: 180, image: 'i', ownership: 'free', price: null, plays: 1, audioUrl: null, credits: null, quality: null, year: 2024 }],
    })

    const result = await albumQuery('al1').queryFn!(ctx)

    expect(apiFetch).toHaveBeenCalledWith('/albums/al1?tracks=true')
    expect(result.album.title).toBe('Album')
    expect(result.tracks).toHaveLength(1)
  })

  it('trackQuery fetches /tracks/:id', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      id: 't1', title: 'Song', artistId: 'a1', artistName: 'Art', albumId: null, albumTitle: null,
      duration: 180, image: 'i', ownership: 'free', price: null, plays: 1, audioUrl: null,
      credits: null, quality: null, year: 2024,
    })

    const result = await trackQuery('t1').queryFn!(ctx)

    expect(apiFetch).toHaveBeenCalledWith('/tracks/t1')
    expect(result.title).toBe('Song')
  })

  it('lyricsQuery fetches /tracks/:id/lyrics', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ lines: [{ time: 0, text: 'la' }] })

    const result = await lyricsQuery('t1').queryFn!(ctx)

    expect(apiFetch).toHaveBeenCalledWith('/tracks/t1/lyrics')
    expect(result).toEqual([{ time: 0, text: 'la' }])
  })

  it('resolveQuery posts the id-lists and maps all four kinds', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      tracks: [{ id: 't1', title: 'A', artistId: 'a1', artistName: 'Art', albumId: null, albumTitle: null, duration: 10, image: 'i', ownership: 'free', price: null, plays: 1, audioUrl: null, credits: null, quality: null, year: null }],
      artists: [{ id: 'a1', name: 'Art', image: 'im', coverImage: null, verified: null, monthlyListeners: null, followers: null, bio: null, location: null, genres: null }],
      albums: [{ id: 'al1', title: 'Alb', artistId: 'a1', artistName: 'Art', year: 2024, coverImage: 'c', genres: null, trackIds: [], tracks: null }],
      playlists: [{ id: 'p1', title: 'PL', description: null, creator: 'C', creatorAvatar: null, image: 'pi', isPublic: true, followers: null, trackIds: ['t1'], tracks: null }],
    })

    const result = await resolveQuery({ trackIds: ['t1'], artistIds: ['a1'], albumIds: ['al1'], playlistIds: ['p1'] }).queryFn!(ctx)

    expect(apiFetch).toHaveBeenCalledWith('/catalog/resolve', {
      method: 'POST',
      body: { trackIds: ['t1'], artistIds: ['a1'], albumIds: ['al1'], playlistIds: ['p1'] },
    })
    expect(result.tracks[0].id).toBe('t1')
    expect(result.artists[0].id).toBe('a1')
    expect(result.albums[0].id).toBe('al1')
    expect(result.playlists[0].id).toBe('p1')
    expect(result.playlists[0].creator).toBe('C')
  })
})
