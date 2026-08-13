import { describe, it, expect } from 'vitest'
import {
  toArtist,
  toTrack,
  toAlbum,
  toAlbumTracks,
  toLyricLines,
  toStoreItem,
  toEvent,
  toTicketTier,
  toPodcast,
  toPodcastEpisode,
  toAdminUserRow,
  toUsersList,
  toUserDetail,
  toCatalogItem,
  toCatalogList,
  toCatalogDetail,
  toCatalogStatus,
  toCatalogType,
  toModerationCase,
  toModerationQueue,
  toFinanceOverview,
  toLedgerPage,
  toPendingPayout,
  toDisputeStatus,
  toDisputeDetail,
  toFeaturedSlot,
  toPushItem,
  toCuratedPlaylist,
  toFeaturedSlotRequest,
  type StoreItemWire,
  type EventWire,
  type TicketTierWire,
  type PodcastWire,
  type PodcastEpisodeWire,
  type PagedUsersWire,
  type UserDetailWire,
  type PagedCatalogWire,
  type CatalogDetailWire,
  type ModerationQueueWire,
  type FinanceOverviewWire,
  type LedgerPageWire,
  type DisputeDetailWire,
  toAdminOverview,
  toHealth,
  toAuditType,
  toAuditPage,
  type AdminOverviewWire,
  type AuditPageWire,
  toSupportTicket,
  toSupportMessage,
  toRiskSignal,
  toRiskBoard,
  toComplianceRequest,
  toPlatformSettings,
  toSettingsRequest,
  toStudioRelease,
  type RiskSignalWire,
  type RiskBoardWire,
  type ComplianceRequestWire,
  type PlatformSettingsWire,
  type StudioReleaseWire,
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

describe('toPodcast', () => {
  it('maps a show, preserving Money on seasonPassPrice', () => {
    const wire: PodcastWire = {
      id: 'the-233-pod',
      title: 'The 233 Podcast',
      publisher: 'Ama Serwaa',
      image: 'x',
      category: 'Culture',
      description: 'Culture talk from Accra.',
      episodeCount: 12,
      popularity: 88,
      seasonPassPrice: { amount: 2000, currency: 'GHS' },
      supportsTips: true,
    }

    const p = toPodcast(wire)

    expect(p).toEqual({
      id: 'the-233-pod',
      title: 'The 233 Podcast',
      publisher: 'Ama Serwaa',
      image: 'x',
      category: 'Culture',
      description: 'Culture talk from Accra.',
      episodeCount: 12,
      popularity: 88,
      seasonPassPrice: { amount: 2000, currency: 'GHS' },
      supportsTips: true,
    })
  })

  it('defaults nullable fields to undefined', () => {
    const wire: PodcastWire = {
      id: 'p2',
      title: 'Naked Truth',
      publisher: 'KMJ',
      image: 'y',
      category: 'Comedy',
      description: null,
      episodeCount: null,
      popularity: null,
      seasonPassPrice: null,
      supportsTips: null,
    }

    const p = toPodcast(wire)

    expect(p.description).toBeUndefined()
    expect(p.episodeCount).toBeUndefined()
    expect(p.popularity).toBeUndefined()
    expect(p.seasonPassPrice).toBeUndefined()
    expect(p.supportsTips).toBeUndefined()
  })
})

describe('toPodcastEpisode', () => {
  it('maps an episode, preserving Money on price', () => {
    const wire: PodcastEpisodeWire = {
      id: 'ep-1',
      podcastId: 'the-233-pod',
      title: 'Kumasi Nights',
      showTitle: 'The 233 Podcast',
      image: 'x',
      duration: 2400,
      publishedAt: '2026-07-01T09:00:00Z',
      description: 'A deep dive.',
      episodeNumber: 12,
      isPremium: true,
      price: { amount: 500, currency: 'GHS' },
      isOwned: false,
      isEarlyAccess: false,
      publicAt: '2026-07-08T09:00:00Z',
    }

    const ep = toPodcastEpisode(wire)

    expect(ep).toEqual({
      id: 'ep-1',
      podcastId: 'the-233-pod',
      title: 'Kumasi Nights',
      showTitle: 'The 233 Podcast',
      image: 'x',
      duration: 2400,
      publishedAt: '2026-07-01T09:00:00Z',
      description: 'A deep dive.',
      episodeNumber: 12,
      isPremium: true,
      price: { amount: 500, currency: 'GHS' },
      isOwned: false,
      isEarlyAccess: false,
      publicAt: '2026-07-08T09:00:00Z',
    })
  })

  it('defaults nullable fields to undefined', () => {
    const wire: PodcastEpisodeWire = {
      id: 'ep-2',
      podcastId: 'p2',
      title: 'Free Episode',
      showTitle: 'Naked Truth',
      image: 'z',
      duration: 1800,
      publishedAt: '2026-06-01T09:00:00Z',
      description: null,
      episodeNumber: null,
      isPremium: null,
      price: null,
      isOwned: null,
      isEarlyAccess: null,
      publicAt: null,
    }

    const ep = toPodcastEpisode(wire)

    expect(ep.description).toBeUndefined()
    expect(ep.episodeNumber).toBeUndefined()
    expect(ep.isPremium).toBeUndefined()
    expect(ep.price).toBeUndefined()
    expect(ep.isOwned).toBeUndefined()
    expect(ep.isEarlyAccess).toBeUndefined()
    expect(ep.publicAt).toBeUndefined()
  })
})

import { toWizardTrack } from './mappers'

describe('toWizardTrack', () => {
  it('unwraps MoneyView price and passes status/duration through', () => {
    const t = toWizardTrack({
      id: 'trk-1', title: 'Intro', duration: 181, status: 'ready', progress: 100,
      src: '/audio/trk-1.m3u8', price: { amount: 2.5, currency: 'GHS' }, explicit: false, position: 0,
    })
    expect(t).toEqual({
      id: 'trk-1', title: 'Intro', duration: 181, status: 'ready',
      progress: 100, src: '/audio/trk-1.m3u8', price: 2.5, explicit: false,
    })
  })

  it('coerces an unknown status to uploading and null src/price to safe defaults', () => {
    const t = toWizardTrack({
      id: 'trk-2', title: 'X', duration: 0, status: 'transcoding', progress: 0,
      src: null as unknown as string, price: null as unknown as { amount: number; currency: string },
      explicit: false, position: 1,
    })
    expect(t.status).toBe('uploading')
    expect(t.src).toBe('')
    expect(t.price).toBe(0)
  })
})

import { toStudioShow, toStudioEpisode } from './mappers'

describe('toStudioShow', () => {
  it('maps id/title/category 1:1', () => {
    expect(toStudioShow({ id: 'sh1', title: 'Konongo Diaries', category: 'Storytelling' }))
      .toEqual({ id: 'sh1', title: 'Konongo Diaries', category: 'Storytelling' })
  })
})

describe('toStudioEpisode', () => {
  it('maps all fields, price wire→number, status passthrough', () => {
    const wire = { id: 'ep1', showId: 'sh1', showTitle: 'Konongo Diaries', title: 'Ep 12',
      duration: 2940, status: 'published', premium: true, price: 5, publishedAt: 'May 02', plays: 18400 }
    expect(toStudioEpisode(wire)).toEqual({
      id: 'ep1', showId: 'sh1', showTitle: 'Konongo Diaries', title: 'Ep 12', duration: 2940,
      status: 'published', premium: true, price: 5, publishedAt: 'May 02', plays: 18400,
    })
  })
  it('coerces a string price to number', () => {
    expect(toStudioEpisode({ id: 'e', showId: 's', showTitle: 'S', title: 'T', duration: 1,
      status: 'draft', premium: false, price: '0', publishedAt: 'x', plays: 0 }).price).toBe(0)
  })
})

import { toPayouts, toPayoutMethod, toPayoutTxn } from './mappers'

describe('toPayoutTxn', () => {
  it('maps a sale (gross present) with money as numbers', () => {
    expect(toPayoutTxn({ id: 't1', date: 'May 02', source: 'Track sale', type: 'Sale',
      gross: 350, net: 245, status: 'cleared' })).toEqual({
      id: 't1', date: 'May 02', source: 'Track sale', type: 'Sale', gross: 350, net: 245, status: 'cleared' })
  })
  it('keeps gross null for a cash-out and coerces string money', () => {
    const r = toPayoutTxn({ id: 't4', date: 'Apr 28', source: 'Withdrawal', type: 'Cash-out',
      gross: null, net: '-5000', status: 'paid' })
    expect(r.gross).toBeNull()
    expect(r.net).toBe(-5000)
  })
})

describe('toPayoutMethod', () => {
  it('maps id/label/detail/kind/isDefault', () => {
    expect(toPayoutMethod({ id: 'm1', label: 'MTN MoMo', detail: '0244 ... 9210', kind: 'momo', isDefault: true }))
      .toEqual({ id: 'm1', label: 'MTN MoMo', detail: '0244 ... 9210', kind: 'momo', isDefault: true })
  })
})

describe('toPayouts', () => {
  it('maps the whole view, coercing money and nested lists', () => {
    const wire = {
      available: 18420.5, pending: 1240.8, thisMonth: 21680, thisMonthDelta: 24, lifetime: 142490,
      since: 'Jan 2024',
      earnings: [{ label: 'May', value: 21680 }],
      bySource: { sales: 12400, royalties: 6420, tips: 2860 },
      methods: [{ id: 'm1', label: 'MTN MoMo', detail: '0244', kind: 'momo', isDefault: true }],
      transactions: [{ id: 't1', date: 'May 02', source: 'Sale', type: 'Sale', gross: 350, net: 245, status: 'cleared' }],
    }
    const p = toPayouts(wire)
    expect(p.available).toBe(18420.5)
    expect(p.earnings[0]).toEqual({ label: 'May', value: 21680 })
    expect(p.bySource).toEqual({ sales: 12400, royalties: 6420, tips: 2860 })
    expect(p.methods[0].id).toBe('m1')
    expect(p.transactions[0].net).toBe(245)
  })
})

describe('admin users mappers', () => {
  const NOW = Date.parse('2025-01-01T12:00:00Z')
  const rowWire = {
    id: 'u1', name: 'Ama Boateng', initial: 'AB', email: 'ama@x.com',
    role: 'artist', verified: true,
    joined: '2024-03-15T10:30:00Z', lastActive: '2025-01-01T10:00:00Z', status: 'active',
  }

  it('toAdminUserRow maps 1:1 with narrowed unions + formatted timestamps', () => {
    const r = toAdminUserRow(rowWire, NOW)
    expect(r).toEqual({
      id: 'u1', name: 'Ama Boateng', initial: 'AB', email: 'ama@x.com',
      role: 'artist', verified: true, joined: '15 Mar 2024', lastActive: '2h ago', status: 'active',
      adminRole: null,
    })
  })

  /**
   * GAP-10. The list derived a role from `is_artist` alone, so every administrator — including the
   * super-admin running the console — was listed as "Fan" on the one screen that enumerates
   * accounts. The console role rides alongside the fan/artist role rather than replacing it: an
   * admin who is also an artist is exactly the case an operator needs to see.
   */
  it('toAdminUserRow carries the console role, and defaults it to null', () => {
    expect(toAdminUserRow({ ...rowWire, adminRole: 'moderator' }, NOW).adminRole).toBe('moderator')
    expect(toAdminUserRow({ ...rowWire, adminRole: null }, NOW).adminRole).toBeNull()
    // A server that predates the field must not produce `undefined` in the row.
    expect(toAdminUserRow(rowWire, NOW).adminRole).toBeNull()
  })

  it('toUsersList maps items + counts', () => {
    const wire: PagedUsersWire = {
      items: [rowWire], page: 1, size: 100, total: 1,
      counts: { all: 10, fans: 7, artists: 3, verified: 2, suspended: 1 },
    }
    const list = toUsersList(wire, NOW)
    expect(list.users).toHaveLength(1)
    expect(list.users[0].name).toBe('Ama Boateng')
    expect(list.users[0].joined).toBe('15 Mar 2024')
    expect(list.users[0].lastActive).toBe('2h ago')
    expect(list.counts).toEqual({ all: 10, fans: 7, artists: 3, verified: 2, suspended: 1 })
  })

  /**
   * This asserted the opposite — that activity/orders/devices were dropped here.
   *
   * Dropping them is what let the detail page substitute a hardcoded fixture, so a real account
   * displayed purchases, tips and follows that never happened. The API sends these arrays (empty,
   * for now, and documented as such); carrying them through is what lets the page render honest
   * empty states today and light up on its own when the endpoint starts filling them.
   */
  it('toUserDetail projects summary + formatted actionLog and carries activity/orders/devices through', () => {
    const wire: UserDetailWire = {
      summary: rowWire,
      activity: [{ junk: true }], orders: [{ junk: true }], devices: [{ junk: true }],
      actionLog: [{ id: 'l1', action: 'Verified artist', by: 'Admin', time: '2025-01-01T11:00:00Z' }],
    }
    const d = toUserDetail(wire, NOW)
    expect(d.summary.id).toBe('u1')
    expect(d.summary.joined).toBe('15 Mar 2024')
    expect(d.actionLog).toEqual([{ id: 'l1', action: 'Verified artist', by: 'Admin', time: '1h ago' }])
    // Passed through untouched — the mapper does not interpret them, so whatever the API sends is
    // what the page sees.
    expect(d.activity).toEqual([{ junk: true }])
    expect(d.orders).toEqual([{ junk: true }])
    expect(d.devices).toEqual([{ junk: true }])
  })

  it('toUserDetail defaults activity/orders/devices to empty arrays when absent', () => {
    const wire = {
      summary: rowWire,
      actionLog: [],
    } as unknown as UserDetailWire
    const d = toUserDetail(wire, NOW)
    expect(d.activity).toEqual([])
    expect(d.orders).toEqual([])
    expect(d.devices).toEqual([])
  })
})

describe('toCatalogStatus', () => {
  it('buckets draft and in_review as pending', () => {
    expect(toCatalogStatus('draft')).toBe('pending')
    expect(toCatalogStatus('in_review')).toBe('pending')
  })

  it('buckets scheduled and live as published', () => {
    expect(toCatalogStatus('scheduled')).toBe('published')
    expect(toCatalogStatus('live')).toBe('published')
  })

  it('maps takedown 1:1', () => {
    expect(toCatalogStatus('takedown')).toBe('takedown')
  })

  it('falls back to pending for an unknown wire value', () => {
    expect(toCatalogStatus('some-future-status')).toBe('pending')
  })
})

describe('toCatalogType', () => {
  it('maps single', () => {
    expect(toCatalogType('single')).toBe('Single')
  })

  it('maps ep', () => {
    expect(toCatalogType('ep')).toBe('EP')
  })

  it('maps album', () => {
    expect(toCatalogType('album')).toBe('Album')
  })

  it('maps mixtape', () => {
    expect(toCatalogType('mixtape')).toBe('Mixtape')
  })

  it('falls back to Compilation (diagnostic sentinel) for an unknown wire value', () => {
    expect(toCatalogType('some-future-type')).toBe('Compilation')
  })
})

describe('admin catalog mappers', () => {
  const rowWire = { id: 'c1', title: 'Iron Boy', note: 'submitted 2h ago', artist: 'Black Sherif', type: 'album', tracks: 14, status: 'pending' }

  it('toCatalogItem maps 1:1 with narrowed unions, translates the wire type, and null note → undefined', () => {
    expect(toCatalogItem(rowWire)).toEqual({
      id: 'c1', title: 'Iron Boy', note: 'submitted 2h ago', artist: 'Black Sherif', type: 'Album', tracks: 14, status: 'pending',
    })
    expect(toCatalogItem({ ...rowWire, note: null }).note).toBeUndefined()
  })

  it('toCatalogItem translates a realistic wire status (in_review) to the pending bucket', () => {
    expect(toCatalogItem({ ...rowWire, status: 'in_review' }).status).toBe('pending')
  })

  it('toCatalogList maps items + the three counts', () => {
    const wire: PagedCatalogWire = { items: [rowWire], page: 1, size: 100, total: 1, counts: { pending: 24, published: 18396, takedown: 8 } }
    const list = toCatalogList(wire)
    expect(list.items).toHaveLength(1)
    expect(list.items[0].title).toBe('Iron Boy')
    expect(list.items[0].type).toBe('Album')
    expect(list.counts).toEqual({ pending: 24, published: 18396, takedown: 8 })
  })

  it('toCatalogDetail translates the wire type, formats duration + relative log time, and projects splits', () => {
    const wire: CatalogDetailWire = {
      id: 'c1', title: 'Iron Boy', note: null, artist: 'Black Sherif', type: 'album', status: 'pending', upc: 'BZ900123', genre: 'Afrobeats',
      tracklist: [{ position: 1, trackId: 't1', title: 'Intro', isrc: 'GHA-26-1001', durationSec: 132, priceMinor: 500 }],
      splits: [{ trackId: 't1', name: 'Black Sherif', role: 'Primary artist', percent: 70, confirmation: 'confirmed' }],
      actionLog: [{ id: 'l1', action: 'Submitted', by: 'system', time: '2026-07-24T10:00:00Z' }],
    }
    const d = toCatalogDetail(wire, 1721815200000) // now = 2024-07-24T10:00:00Z fixed; only checks it's a string
    expect(d.type).toBe('Album')
    expect(d.upc).toBe('BZ900123')
    expect(d.genre).toBe('Afrobeats')
    expect(d.tracks).toEqual([{ position: 1, title: 'Intro', isrc: 'GHA-26-1001', duration: '2:12' }])
    expect(d.splits).toEqual([{ name: 'Black Sherif', role: 'Primary artist', pct: 70 }])
    expect(d.log[0].action).toBe('Submitted')
    expect(typeof d.log[0].time).toBe('string')
  })

  it('toCatalogDetail translates a realistic wire status (in_review) to the pending bucket', () => {
    const wire: CatalogDetailWire = {
      id: 'c1', title: 'Iron Boy', note: null, artist: 'Black Sherif', type: 'album', status: 'in_review', upc: 'BZ900123', genre: 'Afrobeats',
      tracklist: [], splits: [], actionLog: [],
    }
    expect(toCatalogDetail(wire).status).toBe('pending')
  })

  // The page printed a fixed "Hiplife / Drill" for every release. A release with no genre must
  // arrive as null so the page can say so, rather than borrowing a value from nowhere.
  it('toCatalogDetail keeps a missing genre missing', () => {
    const wire: CatalogDetailWire = {
      id: 'c1', title: 'Untagged', note: null, artist: 'Nobody', type: 'single', status: 'in_review', upc: null, genre: null,
      tracklist: [], splits: [], actionLog: [],
    }
    expect(toCatalogDetail(wire).genre).toBeNull()
  })

  it('toCatalogDetail dedupes splits selected across every track of a multi-track release', () => {
    const splitRow = (trackId: string) => [
      { trackId, name: 'Black Sherif', role: 'Primary artist', percent: 70, confirmation: 'confirmed' },
      { trackId, name: 'Beat Butcha', role: 'Producer', percent: 20, confirmation: 'confirmed' },
      { trackId, name: 'Beatzclik Publishing', role: 'Publisher', percent: 10, confirmation: 'confirmed' },
    ]
    const wire: CatalogDetailWire = {
      id: 'c1', title: 'Iron Boy', note: null, artist: 'Black Sherif', type: 'album', status: 'live', upc: 'BZ900123', genre: 'Afrobeats',
      tracklist: [],
      splits: [...splitRow('t1'), ...splitRow('t2')],
      actionLog: [],
    }
    const d = toCatalogDetail(wire)
    expect(d.splits).toEqual([
      { name: 'Black Sherif', role: 'Primary artist', pct: 70 },
      { name: 'Beat Butcha', role: 'Producer', pct: 20 },
      { name: 'Beatzclik Publishing', role: 'Publisher', pct: 10 },
    ])
  })

  it('toCatalogDetail carries null upc through as null', () => {
    const wire: CatalogDetailWire = {
      id: 'c1', title: 'Iron Boy', note: null, artist: 'Black Sherif', type: 'album', status: 'live', upc: null, genre: 'Afrobeats',
      tracklist: [{ position: 1, trackId: 't1', title: 'Intro', isrc: null, durationSec: 132, priceMinor: 500 }],
      splits: [], actionLog: [],
    }
    const d = toCatalogDetail(wire)
    expect(d.upc).toBeNull()
    expect(d.tracks[0].isrc).toBeNull()
  })
})

describe('admin moderation mappers', () => {
  const caseWire = { id: 'm1', item: 'Track · X', reporter: '@dj', reason: 'Copyright', time: '2026-07-24T06:00:00Z', severity: 'high', status: 'open', escalated: false }

  it('toModerationCase maps age via relativeTime and narrows unions', () => {
    const c = toModerationCase(caseWire, Date.parse('2026-07-24T12:00:00Z'))
    expect(c).toEqual({ id: 'm1', item: 'Track · X', reporter: '@dj', reason: 'Copyright', age: '6h', severity: 'high', status: 'open' })
  })

  it('toModerationQueue maps items + summary', () => {
    const wire: ModerationQueueWire = { items: [caseWire], page: 1, size: 100, total: 1, summary: { openCount: 5, slaHours: 6, escalatedCount: 3 } }
    const q = toModerationQueue(wire, Date.parse('2026-07-24T12:00:00Z'))
    expect(q.items).toHaveLength(1)
    expect(q.summary).toEqual({ open: 5, sla: 6, escalated: 3 })
  })
})

describe('finance overview mapper', () => {
  const wire: FinanceOverviewWire = {
    kpis: { gmvMtd: 842000.0, gmvDelta: 12, platformFee: 252600.0, feeTakePct: 30, payoutsDue: 42180.5, payoutsArtists: 318, momoFloat: 96000.0 },
    pendingPayouts: [{ id: 'p1', artist: 'Black Sherif', amount: 12400.0, method: 'MoMo · MTN', status: 'ready' }],
    providerMix: [{ name: 'MTN', value: 62 }, { name: 'Voda', value: 24 }],
    disputes: [{ id: 'd1', kind: 'Refund request', subject: '@ama_b', detail: 'Album not delivered', amount: 18.99, opened: '2026-04-22T10:00:00Z' }],
  }

  it('maps kpis as plain cedis numbers', () => {
    const f = toFinanceOverview(wire)
    expect(f.kpis).toEqual({ gmvMtd: 842000, gmvDelta: 12, platformFee: 252600, feeTakePct: 30, payoutsDue: 42180.5, payoutsArtists: 318, momoFloat: 96000 })
  })

  it('maps pending payouts and narrows the status union', () => {
    const f = toFinanceOverview(wire)
    expect(f.pendingPayouts).toEqual([{ id: 'p1', artist: 'Black Sherif', amount: 12400, method: 'MoMo · MTN', status: 'ready' }])
  })

  it('maps provider mix 1:1 and converts each dispute opened date to a short label', () => {
    const f = toFinanceOverview(wire)
    expect(f.providerMix).toEqual([{ name: 'MTN', value: 62 }, { name: 'Voda', value: 24 }])
    expect(f.disputes[0]).toEqual({ id: 'd1', kind: 'Refund request', subject: '@ama_b', detail: 'Album not delivered', amount: 18.99, opened: 'Apr 22' })
  })
})

describe('ledger mappers', () => {
  it('maps a page: signed amounts, short dates, display-token types, and the server total', () => {
    const wire: LedgerPageWire = {
      items: [
        { id: 'l1', date: '2026-05-02T08:00:00Z', type: 'Sale', party: 'Black Sherif', ref: 'BZ-1', amount: 2.5 },
        { id: 'l2', date: null, type: 'Payout', party: 'DJ Kojo', ref: 'BZ-2', amount: -42180 },
      ],
      page: 2, size: 8, total: 137,
    }
    const p = toLedgerPage(wire)
    expect(p.total).toBe(137)
    expect(p.page).toBe(2)
    expect(p.items[0]).toEqual({ id: 'l1', date: 'May 02', type: 'Sale', party: 'Black Sherif', ref: 'BZ-1', amount: 2.5 })
    expect(p.items[1].amount).toBe(-42180)
    expect(p.items[1].date).toBe('')
  })
})

describe('pending payout mapper', () => {
  it('unwraps the MoneyView envelope (this endpoint differs from the overview)', () => {
    const p = toPendingPayout({ id: 'p1', artist: 'Fido', amount: { amount: 9400.5, currency: 'GHS' }, method: 'MoMo · MTN', status: 'kyc_pending' })
    expect(p).toEqual({ id: 'p1', artist: 'Fido', amount: 9400.5, method: 'MoMo · MTN', status: 'kyc_pending' })
  })
})

describe('dispute detail mapper', () => {
  it('maps the four wire statuses onto the UI two, with escalated still open', () => {
    expect(toDisputeStatus('open')).toBe('open')
    expect(toDisputeStatus('escalated')).toBe('open')
    expect(toDisputeStatus('refunded')).toBe('resolved')
    expect(toDisputeStatus('rejected')).toBe('resolved')
    expect(toDisputeStatus('something-new')).toBe('open')
  })

  it('unwraps MoneyView, shortens opened, and renders timeline times as relative', () => {
    const wire: DisputeDetailWire = {
      id: 'd1', kind: 'Refund request', subject: '@ama_b', detail: 'Album not delivered',
      amount: { amount: 18.99, currency: 'GHS' }, status: 'open', opened: '2026-04-22T10:00:00Z',
      timeline: [{ id: 't1', text: 'Dispute opened by fan', time: '2026-04-22T10:00:00Z' }],
    }
    const d = toDisputeDetail(wire, Date.parse('2026-04-25T10:00:00Z'))
    expect(d.kind).toBe('Refund request')
    expect(d.amount).toBe(18.99)
    expect(d.opened).toBe('Apr 22')
    expect(d.status).toBe('open')
    expect(d.wireStatus).toBe('open')
    expect(d.timeline).toEqual([{ id: 't1', text: 'Dispute opened by fan', time: '3d ago' }])
  })

  it('carries the raw wireStatus through even though the folded status hides it (escalated)', () => {
    const wire: DisputeDetailWire = {
      id: 'd2', kind: 'Chargeback', subject: '@kwesi', detail: 'Card dispute',
      amount: { amount: 40, currency: 'GHS' }, status: 'escalated', opened: null, timeline: [],
    }
    const d = toDisputeDetail(wire)
    expect(d.wireStatus).toBe('escalated')
    expect(d.status).toBe('open')
  })
})

describe('editorial mappers', () => {
  it('toFeaturedSlot maps 1:1 including the sponsored flag', () => {
    expect(toFeaturedSlot({ id: 'f1', title: 'Made in Ghana', note: 'Editorial pick', sponsored: false }))
      .toEqual({ id: 'f1', title: 'Made in Ghana', note: 'Editorial pick', sponsored: false })
    expect(toFeaturedSlot({ id: 'f2', title: 'MTN Presents', note: 'Paid placement', sponsored: true }).sponsored).toBe(true)
  })

  it('maps a null note to an empty string (the column is nullable)', () => {
    expect(toFeaturedSlot({ id: 'f3', title: 'C', note: null, sponsored: false }).note).toBe('')
  })

  it('toPushItem renames the wire timeLabel to the UI time field', () => {
    expect(toPushItem({ id: 'p1', day: 'Fri', timeLabel: '6PM', title: 'New drops', audience: 'All fans', scheduledAt: null }))
      .toEqual({ id: 'p1', day: 'Fri', time: '6PM', title: 'New drops', audience: 'All fans' })
  })

  it('toCuratedPlaylist maps 1:1', () => {
    expect(toCuratedPlaylist({ id: 'pl1', name: 'Hiplife Throwback' })).toEqual({ id: 'pl1', name: 'Hiplife Throwback' })
  })

  it('toFeaturedSlotRequest shapes a slot for the PUT body, defaulting sponsored to false', () => {
    expect(toFeaturedSlotRequest({ id: 'f1', title: 'A', note: 'n', sponsored: true }))
      .toEqual({ id: 'f1', title: 'A', note: 'n', sponsored: true })
    // the mock type makes `sponsored` optional; the wire needs a real boolean
    expect(toFeaturedSlotRequest({ id: 'f2', title: 'B', note: '' }).sponsored).toBe(false)
  })
})
describe('admin overview mapper', () => {
  const wire: AdminOverviewWire = {
    rangeLabel: 'last 7 days',
    kpis: { activeUsers: 1260, streams: 842000, gmv: 51580.5, newArtists: 12, deltas: { users: 0, streams: 15, gmv: -18 } },
    gmvByDay: [1200.5, 800, 0],
    needsAttention: [],
    topArtists: [{ name: 'Black Sherif', revenue: 42180 }],
    paymentMethods: [],
  }

  it('passes bare-cedis money through as plain numbers', () => {
    const o = toAdminOverview(wire)
    expect(o.kpis.gmv).toBe(51580.5)
    expect(o.topArtists).toEqual([{ name: 'Black Sherif', revenue: 42180 }])
    expect(o.gmvByDay).toEqual([1200.5, 800, 0])
  })

  it('preserves a NEGATIVE delta (the backend really produces these)', () => {
    expect(toAdminOverview(wire).kpis.deltas.gmv).toBe(-18)
  })

  it('maps the always-empty Category-B arrays as empty, not fabricated', () => {
    const o = toAdminOverview(wire)
    expect(o.needsAttention).toEqual([])
    expect(o.paymentMethods).toEqual([])
  })
})

describe('health mapper', () => {
  it('carries readiness checks through as metric rows', () => {
    const h = toHealth({
      status: 'normal',
      metrics: [{ label: 'Database connections health check', value: 'UP', sub: 'readiness check' }],
      listeners: [],
      incidents: [],
    })
    expect(h.status).toBe('normal')
    expect(h.metrics).toEqual([
      { label: 'Database connections health check', value: 'UP', sub: 'readiness check' },
    ])
  })

  it('narrows a status it does not recognise to degraded rather than trusting it', () => {
    expect(toHealth({ status: 'something-else', metrics: [], listeners: [], incidents: [] }).status).toBe('degraded')
  })

  /**
   * GAP-04. `unknown` means nothing is being measured — no readiness checks registered, or the
   * probe itself failed. Coercing it to `normal` is the original bug; coercing it to `degraded`
   * would be a different lie (claiming a fault nobody observed). It has to survive as itself so the
   * page can say "Not monitored".
   */
  it('passes unknown through as itself, neither normal nor degraded', () => {
    expect(toHealth({ status: 'unknown', metrics: [], listeners: [], incidents: [] }).status).toBe('unknown')
  })
})

describe('audit mappers', () => {
  it('narrows a known type and falls back to settings for an unknown one', () => {
    expect(toAuditType('finance')).toBe('finance')
    expect(toAuditType('brand-new-type')).toBe('settings')
  })

  it('maps a page: relative time, compound target, and the server total', () => {
    const wire: AuditPageWire = {
      items: [{ id: 'a1', actor: 'Admin · Yaa', action: 'Suspended account', target: 'AdminMember:acc-123', type: 'user', time: '2026-07-25T10:00:00Z' }],
      page: 2, size: 8, total: 91,
    }
    const p = toAuditPage(wire, Date.parse('2026-07-25T12:00:00Z'))
    expect(p.total).toBe(91)
    expect(p.page).toBe(2)
    expect(p.items[0]).toEqual({
      id: 'a1', actor: 'Admin · Yaa', action: 'Suspended account',
      target: 'AdminMember:acc-123', type: 'user', time: '2h ago',
    })
  })
})
const NOW = Date.parse('2026-07-22T12:00:00Z')

describe('toSupportMessage', () => {
  it('maps fields + relative time', () => {
    expect(toSupportMessage({ id: 'm1', from: 'agent', author: 'Yaa', text: 'hi',
      time: '2026-07-22T10:00:00Z' }, NOW)).toEqual({ id: 'm1', from: 'agent', author: 'Yaa', text: 'hi', time: '2h ago' })
  })
})

describe('toSupportTicket', () => {
  it('maps fields, relative age, nested messages', () => {
    const t = toSupportTicket({ id: 't1', subject: 'Payout', requester: 'Black Sherif', channel: 'email',
      priority: 'high', status: 'open', age: '2026-07-22T10:00:00Z',
      messages: [{ id: 'm1', from: 'user', author: 'BS', text: 'q', time: '2026-07-22T11:59:40Z' }] }, NOW)
    expect(t).toEqual({ id: 't1', subject: 'Payout', requester: 'Black Sherif', channel: 'email',
      priority: 'high', status: 'open', age: '2h',
      messages: [{ id: 'm1', from: 'user', author: 'BS', text: 'q', time: 'just now' }] })
  })
})

describe('trust mappers', () => {
  it('maps a signal, narrowing unions and rendering the ISO time as relative', () => {
    const wire: RiskSignalWire = { id: 'r1', subject: '@kwabz', type: 'Chargeback', detail: 'Card · ₵180', level: 'high', time: '2026-07-31T10:00:00Z', status: 'open' }
    const s = toRiskSignal(wire, Date.parse('2026-07-31T12:00:00Z'))
    expect(s).toEqual({ id: 'r1', subject: '@kwabz', type: 'Chargeback', detail: 'Card · ₵180', level: 'high', time: '2h ago', status: 'open' })
  })

  it('falls back safely for an unrecognised level or status rather than trusting the wire', () => {
    const wire: RiskSignalWire = { id: 'r2', subject: 'x', type: 't', detail: 'd', level: 'nonsense', time: null, status: 'nonsense' }
    const s = toRiskSignal(wire)
    expect(s.level).toBe('low')      // least-alarming level, never invents "high"
    expect(s.status).toBe('open')    // never reads as resolved
    expect(s.time).toBe('')
  })

  it('maps the board, carrying the honest-zero KPIs through untouched', () => {
    const wire: RiskBoardWire = { kpis: { chargebackRate: '0%', suspiciousSignups: 0, fraudFlags: 3, botStreams: '0%' }, signals: [] }
    const b = toRiskBoard(wire)
    expect(b.kpis).toEqual({ chargebackRate: '0%', suspiciousSignups: 0, fraudFlags: 3, botStreams: '0%' })
    expect(b.signals).toEqual([])
  })
})

describe('compliance mapper', () => {
  it('renders the due instant as prose and narrows the unions', () => {
    const wire: ComplianceRequestWire = { id: 'c1', type: 'DSAR-export', subject: '@ama_b', detail: 'Data export', due: '2026-08-12T12:00:00Z', status: 'new' }
    const c = toComplianceRequest(wire, Date.parse('2026-07-31T12:00:00Z'))
    expect(c).toEqual({ id: 'c1', type: 'DSAR-export', subject: '@ama_b', detail: 'Data export', due: 'in 12 days', status: 'new' })
  })

  it('shows an em dash for a null due date instead of inventing one', () => {
    const wire: ComplianceRequestWire = { id: 'c2', type: 'Tax', subject: 's', detail: 'd', due: null, status: 'new' }
    expect(toComplianceRequest(wire).due).toBe('—')
  })

  it('derives overdue from the due date since the backend never sets that status', () => {
    const wire: ComplianceRequestWire = { id: 'c3', type: 'Takedown', subject: 's', detail: 'd', due: '2026-07-25T12:00:00Z', status: 'new' }
    const c = toComplianceRequest(wire, Date.parse('2026-07-31T12:00:00Z'))
    expect(c.status).toBe('overdue')
    expect(c.due).toBe('overdue 6 days')
  })

  it('leaves a completed request completed even when its due date is in the past', () => {
    const wire: ComplianceRequestWire = { id: 'c4', type: 'Takedown', subject: 's', detail: 'd', due: '2026-07-25T12:00:00Z', status: 'completed' }
    const c = toComplianceRequest(wire, Date.parse('2026-07-31T12:00:00Z'))
    expect(c.status).toBe('completed')
    expect(c.due).toBe('completed')
  })
})

describe('platform settings mappers', () => {
  const wire: PlatformSettingsWire = {
    platformFeePct: 30, payoutDay: 'Friday', payoutMinimum: 10, defaultCurrency: 'GHS', maintenanceMode: false,
    providers: { mtn: true, telecel: true, airteltigo: true, card: true, bank: true },
    flags: { artistSignups: true, podcasts: true, events: false, tipping: true, fanMessaging: false },
  }

  it('passes the integer percent and bare-cedis minimum straight through', () => {
    const s = toPlatformSettings(wire)
    expect(s.platformFeePct).toBe(30)   // integer percent, not a fraction
    expect(s.payoutMinimum).toBe(10)    // bare cedis; the server owns the minor-unit conversion
    expect(s.flags.events).toBe(false)
  })

  it('toSettingsRequest sends the COMPLETE object (the PUT is a full replace, not a patch)', () => {
    const body = toSettingsRequest(toPlatformSettings(wire))
    expect(Object.keys(body).sort()).toEqual(
      ['defaultCurrency', 'flags', 'maintenanceMode', 'payoutDay', 'payoutMinimum', 'platformFeePct', 'providers'].sort(),
    )
    expect(body.providers).toEqual(wire.providers)
    expect(body.flags).toEqual(wire.flags)
  })
})

describe('toStudioRelease', () => {
  const base: StudioReleaseWire = {
    id: 'r1', title: 'Iron Boy', type: 'album', status: 'draft', date: '—',
    trackCount: 14, streams: 0, revenue: { amount: 0, currency: 'GHS' }, price: { amount: 2.5, currency: 'GHS' },
    downloadable: null,
  }

  it('carries a missing download choice through as null rather than false', () => {
    const wire: StudioReleaseWire = { ...base, downloadable: null }
    expect(toStudioRelease(wire).downloadable).toBeNull()
  })

  it('carries an explicit "no downloads" choice through as false, not null', () => {
    const wire: StudioReleaseWire = { ...base, downloadable: false }
    expect(toStudioRelease(wire).downloadable).toBe(false)
  })

  it('carries an explicit "allow downloads" choice through as true', () => {
    const wire: StudioReleaseWire = { ...base, downloadable: true }
    expect(toStudioRelease(wire).downloadable).toBe(true)
  })
})
