import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as client from '../client'
import {
  studioAnalyticsQuery, studioAudienceQuery,
  studioProfileQuery, studioSettingsQuery, apiSaveStudioProfile, apiSaveStudioSettings,
} from './studio'

vi.mock('../client')

const series = (total: number) => ({ total, delta: 5, current: [total / 2, total / 2], previous: [1, 1] })

const analyticsWire = {
  rangeLabel: 'Last 28 days',
  axisLabel: 'Day',
  labels: ['Jul 1', 'Jul 2'],
  metrics: { streams: series(1200), sales: series(30), followers: series(90), tips: series(8) },
  fans: 2400,
  countries: [{ name: 'Ghana', value: 70 }],
  topTracks: [{ title: 'Soja', streams: 500, revenue: 12.5 }],
  ages: [{ label: '18-24', value: 40 }],
  revenue: { sales: 100, streaming: 20, tips: 8 },
  engagement: { completion: 80, save: 12, skip: 8 },
  sources: [{ name: 'Search', pct: 35 }],
}

const audienceWire = {
  rangeLabel: 'Last 7 days',
  monthlyListeners: 2_400_000,
  listenersDelta: 5,
  followers: 120_000,
  followersGained: 540,
  followersPeriod: 'this week',
  superfans: 320,
  avgSessionSec: 1_800,
  avgSessionDelta: 3,
  cities: [{ name: 'Accra', value: 45 }],
  gender: { male: 60, female: 38, other: 2 },
  ages: [{ label: '18-24', value: 40 }],
  superfansList: [{ handle: '@ama_b', initial: 'A', tracks: 12, tipped: 40 }],
}

beforeEach(() => vi.resetAllMocks())

describe('studioAnalyticsQuery', () => {
  it('requests the given range and maps the view, including the metric series', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue(analyticsWire)

    const a = await studioAnalyticsQuery('28d').queryFn!({} as never)

    expect(client.apiFetch).toHaveBeenCalledWith('/studio/analytics?range=28d')
    expect(a.rangeLabel).toBe('Last 28 days')
    expect(a.metrics.streams).toEqual({ total: 1200, delta: 5, current: [600, 600], previous: [1, 1] })
    expect(a.metrics.tips.total).toBe(8)
    expect(a.countries[0]).toEqual({ name: 'Ghana', value: 70 })
    expect(a.topTracks[0]).toEqual({ title: 'Soja', streams: 500, revenue: 12.5 })
    expect(a.revenue).toEqual({ sales: 100, streaming: 20, tips: 8 })
    expect(a.engagement).toEqual({ completion: 80, save: 12, skip: 8 })
    expect(a.sources[0]).toEqual({ name: 'Search', pct: 35 })
  })

  it('scopes the query key by range so switching range refetches', () => {
    expect(studioAnalyticsQuery('7d').queryKey).not.toEqual(studioAnalyticsQuery('28d').queryKey)
  })
})

describe('studioAudienceQuery', () => {
  it('requests the given range and maps the view, including gender + superfans', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue(audienceWire)

    const a = await studioAudienceQuery('7d').queryFn!({} as never)

    expect(client.apiFetch).toHaveBeenCalledWith('/studio/audience?range=7d')
    expect(a.monthlyListeners).toBe(2_400_000)
    expect(a.followersPeriod).toBe('this week')
    expect(a.avgSessionSec).toBe(1_800)
    expect(a.cities[0]).toEqual({ name: 'Accra', value: 45 })
    expect(a.gender).toEqual({ male: 60, female: 38, other: 2 })
    expect(a.superfansList[0]).toEqual({ handle: '@ama_b', initial: 'A', tracks: 12, tipped: 40 })
  })

  it('scopes the query key by range', () => {
    expect(studioAudienceQuery('7d').queryKey).not.toEqual(studioAudienceQuery('90d').queryKey)
  })
})

const profileWire = {
  displayName: 'Kojo Beats', username: '@kojo', hometown: 'Accra, Ghana',
  genres: ['Afrobeats'], bio: 'Producer.', avatar: null, banner: null,
  links: { instagram: 'kojo', twitter: '', youtube: '', website: '' },
  shows: [{ id: 's1', venue: 'Alliance', date: 'Aug 1', city: 'Accra' }],
  featuredTrackId: 't1', bookingEmail: 'book@kojo.com',
  pressAssets: [{ id: 'a1', name: 'kit.pdf', url: 'https://x/kit.pdf' }],
}

describe('studioProfileQuery', () => {
  it('requests /studio/profile and maps the view through', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue(profileWire)
    const p = await studioProfileQuery().queryFn!({} as never)
    expect(client.apiFetch).toHaveBeenCalledWith('/studio/profile')
    expect(p.displayName).toBe('Kojo Beats')
    expect(p.shows[0]).toEqual({ id: 's1', venue: 'Alliance', date: 'Aug 1', city: 'Accra' })
  })
})

describe('apiSaveStudioProfile', () => {
  it('PUTs the profile and strips blob: avatar/banner/press assets', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue(profileWire)
    await apiSaveStudioProfile({
      ...profileWire, avatar: 'blob:local', banner: 'https://x/b.jpg',
      pressAssets: [{ id: 'a1', name: 'k', url: 'blob:x' }, { id: 'a2', name: 'ok', url: 'https://x/ok' }],
    } as never)
    const [path, opts] = vi.mocked(client.apiFetch).mock.calls[0]
    expect(path).toBe('/studio/profile')
    expect(opts).toMatchObject({ method: 'PUT' })
    expect(opts!.body).toMatchObject({ avatar: null, banner: 'https://x/b.jpg' })
    expect((opts!.body as { pressAssets: unknown[] }).pressAssets).toHaveLength(1)
  })
})

const settingsWire = {
  email: 'x@y.com', phone: '024', country: 'GH', language: 'English', timezone: 'GMT',
  twoFactor: true, sessions: [{ id: 's' }], connectedApps: [{ id: 'a' }],
  verification: { artist: true }, billing: { plan: 'Pro' },
  notifications: { sales: true, tips: false, followers: false, payouts: false, weeklySummary: false, comments: false, marketing: false },
  defaults: { trackPrice: 2, releaseVisibility: 'public', autoExplicit: false, allowOffers: true },
  payouts: { autoWithdraw: false, autoWithdrawThreshold: 0, taxId: '' },
  privacy: { discoverable: true, showRealName: false, acceptBookings: true, allowDms: false },
  team: [{ id: 'u1', name: 'A', email: 'a@b.com', role: 'Manager' }],
}

describe('studioSettingsQuery', () => {
  it('requests /studio/settings and maps the view through', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue(settingsWire)
    const s = await studioSettingsQuery().queryFn!({} as never)
    expect(client.apiFetch).toHaveBeenCalledWith('/studio/settings')
    expect(s.email).toBe('x@y.com')
    expect(s.notifications.sales).toBe(true)
    expect(s.defaults.trackPrice).toBe(2)
  })
})

describe('apiSaveStudioSettings', () => {
  it('PUTs ONLY the Category-A subset (drops email/twoFactor/sessions/etc.)', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue({})
    await apiSaveStudioSettings({
      email: 'x@y.com', phone: '024', country: 'GH', language: 'English', timezone: 'GMT',
      twoFactor: true, sessions: [{ id: 's' }], connectedApps: [{ id: 'a' }],
      verification: { artist: true }, billing: { plan: 'Pro' },
      notifications: { sales: true, tips: false, followers: false, payouts: false, weeklySummary: false, comments: false, marketing: false },
      defaults: { trackPrice: 2, releaseVisibility: 'public', autoExplicit: false, allowOffers: true },
      payouts: { autoWithdraw: false, autoWithdrawThreshold: 0, taxId: '' },
      privacy: { discoverable: true, showRealName: false, acceptBookings: true, allowDms: false },
      team: [{ id: 'u1', name: 'A', email: 'a@b.com', role: 'Manager' }],
    } as never)
    const body = vi.mocked(client.apiFetch).mock.calls[0][1]!.body as Record<string, unknown>
    expect(Object.keys(body).sort()).toEqual(['defaults', 'notifications', 'payouts', 'privacy', 'team'])
    expect(body.notifications).toMatchObject({ sales: true })
  })
})
