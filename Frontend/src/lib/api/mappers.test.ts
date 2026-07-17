import { describe, it, expect } from 'vitest'
import {
  toArtist,
  toTrack,
  toAlbum,
  toAlbumTracks,
  toBrowseCategory,
  toLyricLines,
  toStoreItem,
  toEvent,
  toTicketTier,
  type StoreItemWire,
  type EventWire,
  type TicketTierWire,
} from './mappers'

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

describe('toStoreItem', () => {
  it('maps a merch item, converting nulls to undefined and keeping price as Money', () => {
    const wire: StoreItemWire = {
      id: 'merch-bsherif-tee',
      type: 'MERCH',
      title: 'Iron Boy Tour Tee',
      artistName: 'Black Sherif',
      artistId: 'black-sherif',
      image: 'https://img/tee.jpg',
      price: { amount: 120, currency: 'GHS' },
      genre: null,
      badges: ['LIMITED'],
      description: 'Official tour merch.',
      popularity: null,
      createdAt: null,
      licenseOptions: null,
      variants: [{ label: 'Size', options: ['S', 'M', 'L', 'XL'] }],
      quality: null,
      dropsAt: null,
      stockRemaining: 42,
    }

    const item = toStoreItem(wire)

    expect(item).toEqual({
      id: 'merch-bsherif-tee',
      type: 'MERCH',
      title: 'Iron Boy Tour Tee',
      artistName: 'Black Sherif',
      artistId: 'black-sherif',
      image: 'https://img/tee.jpg',
      price: { amount: 120, currency: 'GHS' },
      badges: ['LIMITED'],
      description: 'Official tour merch.',
      variants: [{ label: 'Size', options: ['S', 'M', 'L', 'XL'] }],
      stockRemaining: 42,
    })
  })

  it('maps a beat-license item, mapping nested licenseOptions with their own Money price', () => {
    const wire: StoreItemWire = {
      id: 'beat-drill-001',
      type: 'BEAT_LICENSE',
      title: 'Cold Nights',
      artistName: 'Yaw Tog',
      artistId: null,
      image: 'https://img/beat.jpg',
      price: { amount: 50, currency: 'GHS' },
      genre: 'Drill',
      badges: null,
      description: null,
      popularity: 87,
      createdAt: '2026-01-05T00:00:00Z',
      licenseOptions: [
        { tier: 'LEASE', label: 'Lease', price: { amount: 50, currency: 'GHS' }, features: ['MP3'], terms: null },
        {
          tier: 'EXCLUSIVE',
          label: 'Exclusive',
          price: { amount: 900, currency: 'GHS' },
          features: ['WAV', 'Stems', 'Full rights'],
          terms: 'Unlimited streams',
        },
      ],
      variants: null,
      quality: null,
      dropsAt: null,
      stockRemaining: null,
    }

    const item = toStoreItem(wire)

    expect(item.artistId).toBeUndefined()
    expect(item.genre).toBe('Drill')
    expect(item.popularity).toBe(87)
    expect(item.createdAt).toBe('2026-01-05T00:00:00Z')
    expect(item.licenseOptions).toEqual([
      { tier: 'LEASE', label: 'Lease', price: { amount: 50, currency: 'GHS' }, features: ['MP3'], terms: undefined },
      {
        tier: 'EXCLUSIVE',
        label: 'Exclusive',
        price: { amount: 900, currency: 'GHS' },
        features: ['WAV', 'Stems', 'Full rights'],
        terms: 'Unlimited streams',
      },
    ])
  })
})

describe('toTicketTier', () => {
  it('maps a tier, preserving Money and defaulting nullable fields', () => {
    const wire: TicketTierWire = {
      name: 'Regular',
      price: { amount: 150, currency: 'GHS' },
      perks: ['General standing'],
      soldOut: false,
    }

    const tier = toTicketTier(wire)

    expect(tier).toEqual({
      name: 'Regular',
      price: { amount: 150, currency: 'GHS' },
      perks: ['General standing'],
      soldOut: false,
    })
  })

  it('defaults nullable perks/soldOut to undefined', () => {
    const wire: TicketTierWire = {
      name: 'VIP',
      price: { amount: 500, currency: 'GHS' },
      perks: null,
      soldOut: null,
    }

    const tier = toTicketTier(wire)

    expect(tier.perks).toBeUndefined()
    expect(tier.soldOut).toBeUndefined()
  })
})

describe('toEvent', () => {
  it('maps an event and its tiers, preserving Money on tier price', () => {
    const wire: EventWire = {
      id: 'iron-boy-live',
      title: 'Iron Boy Live',
      artistName: 'Black Sherif',
      artistId: 'black-sherif',
      lineup: ['DJ Vyrusky'],
      image: 'x',
      date: '2026-07-09T19:00:00Z',
      doorsTime: '7:00 PM',
      venue: 'Independence Square, Accra',
      city: 'Accra',
      region: 'Greater Accra',
      status: 'selling-fast',
      category: 'Concert',
      description: 'The comeback show.',
      ticketTiers: [
        { name: 'Regular', price: { amount: 150, currency: 'GHS' }, perks: ['General standing'], soldOut: false },
      ],
      popularity: 92,
      ageRestriction: '18+',
    }

    const ev = toEvent(wire)

    expect(ev.id).toBe('iron-boy-live')
    expect(ev.title).toBe('Iron Boy Live')
    expect(ev.artistName).toBe('Black Sherif')
    expect(ev.artistId).toBe('black-sherif')
    expect(ev.lineup).toEqual(['DJ Vyrusky'])
    expect(ev.image).toBe('x')
    expect(ev.date).toBe('2026-07-09T19:00:00Z')
    expect(ev.doorsTime).toBe('7:00 PM')
    expect(ev.venue).toBe('Independence Square, Accra')
    expect(ev.city).toBe('Accra')
    expect(ev.region).toBe('Greater Accra')
    expect(ev.status).toBe('selling-fast')
    expect(ev.category).toBe('Concert')
    expect(ev.description).toBe('The comeback show.')
    expect(ev.popularity).toBe(92)
    expect(ev.ageRestriction).toBe('18+')
    expect(ev.ticketTiers).toEqual([
      { name: 'Regular', price: { amount: 150, currency: 'GHS' }, perks: ['General standing'], soldOut: false },
    ])
  })

  it('defaults nullable fields to undefined', () => {
    const wire: EventWire = {
      id: 'e2',
      title: 'Club Night',
      artistName: 'DJ Someone',
      artistId: null,
      lineup: null,
      image: 'y',
      date: '2026-08-01T20:00:00Z',
      doorsTime: null,
      venue: 'The Venue',
      city: 'Kumasi',
      region: null,
      status: 'on-sale',
      category: 'Club Night',
      description: null,
      ticketTiers: [],
      popularity: null,
      ageRestriction: null,
    }

    const ev = toEvent(wire)

    expect(ev.artistId).toBeUndefined()
    expect(ev.lineup).toBeUndefined()
    expect(ev.doorsTime).toBeUndefined()
    expect(ev.region).toBeUndefined()
    expect(ev.description).toBeUndefined()
    expect(ev.popularity).toBeUndefined()
    expect(ev.ageRestriction).toBeUndefined()
    expect(ev.ticketTiers).toEqual([])
  })
})
