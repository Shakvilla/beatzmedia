import { describe, it, expect, vi, beforeEach } from 'vitest'
import { apiFetch } from '../client'
import {
  collectionQuery,
  EMPTY_COLLECTION,
  toggleMembership,
  setOwned,
  putPlaylist,
  removePlaylistById,
  renamePlaylistById,
  apiToggleMembership,
  apiCreatePlaylist,
  apiAddTrack,
  type CollectionData,
} from './collection'

vi.mock('../client', () => ({ apiFetch: vi.fn() }))

const base: CollectionData = {
  likedTracks: ['t1'],
  followedArtists: [],
  followedPlaylists: [],
  followedShows: [],
  savedAlbums: [],
  ownedTracks: [],
  downloadableTracks: [],
  userPlaylists: [{ id: 'p1', title: 'Mine', trackIds: ['t1'], createdAt: '2026-01-01' }],
}

describe('collection transforms (pure)', () => {
  it('toggleMembership adds when absent and removes when present', () => {
    const added = toggleMembership(base, 'followedArtists', 'a1')
    expect(added.followedArtists).toEqual(['a1'])
    const removed = toggleMembership(base, 'likedTracks', 't1')
    expect(removed.likedTracks).toEqual([])
    // input not mutated
    expect(base.likedTracks).toEqual(['t1'])
  })

  it('setOwned unions ids without duplicates', () => {
    const next = setOwned({ ...base, ownedTracks: ['t1'] }, ['t1', 't2'])
    expect(next.ownedTracks.sort()).toEqual(['t1', 't2'])
  })

  it('putPlaylist prepends, removePlaylistById drops, renamePlaylistById renames', () => {
    const pl = { id: 'p2', title: 'New', trackIds: [], createdAt: '2026-02-02' }
    expect(putPlaylist(base, pl).userPlaylists[0].id).toBe('p2')
    expect(removePlaylistById(base, 'p1').userPlaylists).toEqual([])
    expect(renamePlaylistById(base, 'p1', 'Renamed').userPlaylists[0].title).toBe('Renamed')
  })
})

describe('collection API calls', () => {
  beforeEach(() => vi.mocked(apiFetch).mockReset())

  it('collectionQuery maps the wire view, nulling playlist description to undefined', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      likedTracks: ['t1'], followedArtists: [], followedPlaylists: [], followedShows: [],
      savedAlbums: [], ownedTracks: ['t9'], downloadableTracks: ['t9'],
      userPlaylists: [{ id: 'p1', title: 'Mine', description: null, trackIds: ['t1'], createdAt: '2026-01-01' }],
    })
    const data = await collectionQuery().queryFn!({} as any)
    expect(apiFetch).toHaveBeenCalledWith('/me/collection')
    expect(data.ownedTracks).toEqual(['t9'])
    expect(data.downloadableTracks).toEqual(['t9'])
    expect(data.userPlaylists[0].description).toBeUndefined()
  })

  it('ownedTracks and downloadableTracks pass through as two independent sets, not one derived from the other', async () => {
    // toCollectionData copies both wire fields verbatim (no client-side filtering/derivation): a
    // track can be in ownedTracks without being in downloadableTracks. This test only pins that
    // pass-through shape; it does NOT exercise the backend reason the two sets can differ (the
    // grant, not the release, gates downloadableTracks — see GrantOwnershipService /
    // CatalogExpansionReaderAdapter#isDownloadable), which is covered on the backend instead.
    vi.mocked(apiFetch).mockResolvedValue({
      likedTracks: [], followedArtists: [], followedPlaylists: [], followedShows: [],
      savedAlbums: [], ownedTracks: ['t9', 't10'], downloadableTracks: ['t9'],
      userPlaylists: [],
    })
    const data = await collectionQuery().queryFn!({} as any)
    expect(data.ownedTracks).toEqual(['t9', 't10'])
    expect(data.downloadableTracks).toEqual(['t9'])
  })

  it('apiToggleMembership uses PUT to add and DELETE to remove', async () => {
    vi.mocked(apiFetch).mockResolvedValue(undefined)
    await apiToggleMembership('likes/tracks', 't1', true)
    expect(apiFetch).toHaveBeenCalledWith('/me/likes/tracks/t1', { method: 'PUT' })
    await apiToggleMembership('follows/artists', 'a1', false)
    expect(apiFetch).toHaveBeenCalledWith('/me/follows/artists/a1', { method: 'DELETE' })
  })

  it('apiCreatePlaylist posts the title and returns the created playlist', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ id: 'p9', title: 'X', description: null, trackIds: [], createdAt: '2026-03-03' })
    const pl = await apiCreatePlaylist('X')
    expect(apiFetch).toHaveBeenCalledWith('/me/playlists', { method: 'POST', body: { title: 'X' } })
    expect(pl.id).toBe('p9')
    expect(pl.description).toBeUndefined()
  })

  it('apiAddTrack PUTs and returns the updated playlist', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ id: 'p1', title: 'Mine', description: null, trackIds: ['t1', 't2'], createdAt: '2026-01-01' })
    const pl = await apiAddTrack('p1', 't2')
    expect(apiFetch).toHaveBeenCalledWith('/me/playlists/p1/tracks/t2', { method: 'PUT' })
    expect(pl.trackIds).toEqual(['t1', 't2'])
  })

  it('EMPTY_COLLECTION has all empty arrays', () => {
    expect(EMPTY_COLLECTION.likedTracks).toEqual([])
    expect(EMPTY_COLLECTION.userPlaylists).toEqual([])
  })
})
