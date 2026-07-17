import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as client from '../client'
import { studioAnalyticsQuery, studioAudienceQuery } from './studio'

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
