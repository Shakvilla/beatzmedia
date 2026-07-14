import { describe, it, expect } from 'vitest'
import { toArtist, toTrack, toAlbum, toAlbumTracks, toBrowseCategory, toLyricLines } from './mappers'

describe('toArtist', () => {
  it('maps a full wire artist, converting nulls to undefined', () => {
    const artist = toArtist({
      id: 'a1',
      name: 'Black Sherif',
      image: 'img.jpg',
      coverImage: null,
      verified: true,
      monthlyListeners: 1000,
      followers: 500,
      bio: null,
      location: null,
      genres: null,
    })

    expect(artist).toEqual({
      id: 'a1',
      name: 'Black Sherif',
      image: 'img.jpg',
      coverImage: undefined,
      verified: true,
      monthlyListeners: 1000,
      followers: 500,
      bio: undefined,
      location: undefined,
      genres: undefined,
    })
  })
})

describe('toTrack', () => {
  it('maps a wire track including nested price', () => {
    const track = toTrack({
      id: 't1',
      title: 'Song',
      artistId: 'a1',
      artistName: 'Black Sherif',
      albumId: null,
      albumTitle: null,
      duration: 180,
      image: 'i.jpg',
      ownership: 'for-sale',
      price: { amount: 5, currency: 'GHS' },
      plays: 42,
      audioUrl: null,
      credits: null,
      quality: null,
      year: 2024,
    })

    expect(track.ownership).toBe('for-sale')
    expect(track.price).toEqual({ amount: 5, currency: 'GHS' })
    expect(track.albumId).toBeUndefined()
  })
})

describe('toAlbum / toAlbumTracks', () => {
  const wire = {
    id: 'al1',
    title: 'Album',
    artistId: 'a1',
    artistName: 'Black Sherif',
    year: 2024,
    coverImage: 'c.jpg',
    genres: ['Afrobeats'],
    trackIds: ['t1'],
    tracks: [
      {
        id: 't1',
        title: 'Song',
        artistId: 'a1',
        artistName: 'Black Sherif',
        albumId: 'al1',
        albumTitle: 'Album',
        duration: 180,
        image: 'i.jpg',
        ownership: 'free',
        price: null,
        plays: 10,
        audioUrl: null,
        credits: null,
        quality: null,
        year: 2024,
      },
    ],
  }

  it('maps the album without the embedded tracks field', () => {
    const album = toAlbum(wire)
    expect(album).toEqual({
      id: 'al1',
      title: 'Album',
      artistId: 'a1',
      artistName: 'Black Sherif',
      year: 2024,
      coverImage: 'c.jpg',
      genres: ['Afrobeats'],
      trackIds: ['t1'],
    })
  })

  it('maps the embedded tracks separately', () => {
    const tracks = toAlbumTracks(wire)
    expect(tracks).toHaveLength(1)
    expect(tracks[0].title).toBe('Song')
  })

  it('returns an empty array when tracks were not requested', () => {
    expect(toAlbumTracks({ ...wire, tracks: null })).toEqual([])
  })
})

describe('toBrowseCategory', () => {
  it('passes fields through unchanged', () => {
    expect(toBrowseCategory({ id: 'c1', title: 'Afrobeats', colorClass: 'bg-red-500' })).toEqual({
      id: 'c1',
      title: 'Afrobeats',
      colorClass: 'bg-red-500',
    })
  })
})

describe('toLyricLines', () => {
  it('returns the lines array', () => {
    expect(toLyricLines({ lines: [{ time: 0, text: 'la la' }] })).toEqual([{ time: 0, text: 'la la' }])
  })
})
