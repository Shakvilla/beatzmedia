import { render, screen, cleanup } from '@testing-library/react'
import { describe, it, expect, vi, afterEach } from 'vitest'
import { EpisodeRow } from './episode-row'
import type { PodcastEpisode } from '../../../types'

afterEach(cleanup)

const freeEpisode: PodcastEpisode = {
  id: 'ep-1',
  podcastId: 'the-233-pod',
  title: 'Ep 1',
  showTitle: 'The 233 Podcast',
  image: 'x',
  duration: 1800,
  publishedAt: '2026-07-01T00:00:00Z',
  isPremium: false,
}

describe('EpisodeRow', () => {
  it('renders the passed episode without touching podcast-data', () => {
    render(<EpisodeRow episode={freeEpisode} isCurrent={false} isPlaying={false} onPlay={vi.fn()} />)
    expect(screen.getByText('Ep 1')).toBeTruthy()
    expect(screen.getByText('The 233 Podcast')).toBeTruthy()
  })

  it('locks a premium episode the user does not own and shows the buy price', () => {
    const locked: PodcastEpisode = {
      ...freeEpisode,
      id: 'ep-2',
      title: 'Locked Ep',
      isPremium: true,
      isOwned: false,
      price: { amount: 500, currency: 'GHS' },
    }
    render(<EpisodeRow episode={locked} isCurrent={false} isPlaying={false} onPlay={vi.fn()} onBuy={vi.fn()} />)
    expect(screen.getByText(/Buy/)).toBeTruthy()
  })

  it('lets an owned premium episode play', () => {
    const owned: PodcastEpisode = {
      ...freeEpisode,
      id: 'ep-3',
      title: 'Owned Ep',
      isPremium: true,
      isOwned: true,
      price: { amount: 500, currency: 'GHS' },
    }
    render(<EpisodeRow episode={owned} isCurrent={false} isPlaying={false} onPlay={vi.fn()} />)
    expect(screen.getByLabelText('Play episode')).toBeTruthy()
  })
})
