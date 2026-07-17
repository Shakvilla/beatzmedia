import { describe, it, expect } from 'vitest'
import { episodeAccessible } from './episode-access'
import type { PodcastEpisode } from '../../types'

describe('episodeAccessible', () => {
  const baseEpisode: PodcastEpisode = {
    id: 'ep-1',
    podcastId: 'pod-1',
    title: 'Test Episode',
    showTitle: 'Test Podcast',
    image: 'image.jpg',
    duration: 3600,
    publishedAt: '2026-01-01T00:00:00Z',
  }

  it('returns true when episode is owned', () => {
    const episode = {
      ...baseEpisode,
      isOwned: true,
      isPremium: true,
    } as PodcastEpisode

    expect(episodeAccessible(episode)).toBe(true)
  })

  it('returns false when early-access and publicAt is in the future', () => {
    const futureDate = new Date(Date.now() + 86400000).toISOString() // 24h from now
    const episode = {
      ...baseEpisode,
      isEarlyAccess: true,
      publicAt: futureDate,
      isOwned: false,
      isPremium: false,
    } as PodcastEpisode

    expect(episodeAccessible(episode)).toBe(false)
  })

  it('returns true when early-access and publicAt is in the past', () => {
    const pastDate = new Date(Date.now() - 86400000).toISOString() // 24h ago
    const episode = {
      ...baseEpisode,
      isEarlyAccess: true,
      publicAt: pastDate,
      isOwned: false,
      isPremium: false,
    } as PodcastEpisode

    expect(episodeAccessible(episode)).toBe(true)
  })

  it('returns false when premium and not owned', () => {
    const episode = {
      ...baseEpisode,
      isPremium: true,
      isOwned: false,
      isEarlyAccess: false,
    } as PodcastEpisode

    expect(episodeAccessible(episode)).toBe(false)
  })

  it('returns true when free (non-premium)', () => {
    const episode = {
      ...baseEpisode,
      isPremium: false,
      isOwned: false,
      isEarlyAccess: false,
    } as PodcastEpisode

    expect(episodeAccessible(episode)).toBe(true)
  })
})
