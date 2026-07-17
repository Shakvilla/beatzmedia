import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as client from '../client'
import { podcastsListQuery, podcastQuery, podcastEpisodesQuery } from './podcasts'

vi.mock('../client')

const show = {
  id: 'the-233-pod',
  title: 'The 233 Podcast',
  publisher: 'Ama Serwaa',
  image: 'x',
  category: 'Culture',
  description: null,
  episodeCount: 1,
  popularity: 42,
  seasonPassPrice: { amount: 5000, currency: 'GHS' },
  supportsTips: true,
}

const ep = {
  id: 'ep-1',
  podcastId: 'the-233-pod',
  title: 'Ep 1',
  showTitle: 'The 233 Podcast',
  image: 'x',
  duration: 1800,
  publishedAt: '2026-07-01T00:00:00Z',
  description: null,
  episodeNumber: 1,
  isPremium: true,
  price: { amount: 1000, currency: 'GHS' },
  isOwned: false,
  isEarlyAccess: false,
  publicAt: null,
}

beforeEach(() => vi.resetAllMocks())

describe('podcasts queries', () => {
  it('lists shows (page)', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue({ items: [show], page: 1, size: 20, total: 1 })
    const shows = await podcastsListQuery().queryFn!({} as never)
    expect(client.apiFetch).toHaveBeenCalledWith('/podcasts?page=1&size=48')
    expect(shows[0].id).toBe('the-233-pod')
    expect(shows[0].publisher).toBe('Ama Serwaa')
    expect(shows[0].seasonPassPrice).toEqual({ amount: 5000, currency: 'GHS' })
  })

  it('lists shows filtered by category', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue({ items: [show], page: 1, size: 20, total: 1 })
    await podcastsListQuery({ category: 'Culture' }).queryFn!({} as never)
    expect(client.apiFetch).toHaveBeenCalledWith('/podcasts?category=Culture&page=1&size=48')
  })

  it('fetches a show + its episodes', async () => {
    vi.mocked(client.apiFetch).mockResolvedValueOnce(show)
    const p = await podcastQuery('the-233-pod').queryFn!({} as never)
    expect(client.apiFetch).toHaveBeenCalledWith('/podcasts/the-233-pod')
    expect(p.title).toBe('The 233 Podcast')

    vi.mocked(client.apiFetch).mockResolvedValueOnce([ep])
    const eps = await podcastEpisodesQuery('the-233-pod').queryFn!({} as never)
    expect(client.apiFetch).toHaveBeenCalledWith('/podcasts/the-233-pod/episodes')
    expect(eps[0].id).toBe('ep-1')
    expect(eps[0].duration).toBe(1800)
    expect(eps[0].price).toEqual({ amount: 1000, currency: 'GHS' })
  })
})
