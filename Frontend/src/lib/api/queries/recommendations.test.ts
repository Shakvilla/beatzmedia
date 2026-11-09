import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRecommendations } from './recommendations'

const apiFetch = vi.hoisted(() => vi.fn())

vi.mock('../client', () => ({ apiFetch }))

describe('apiRecommendations', () => {
  beforeEach(() => apiFetch.mockReset())

  it('normalizes legacy runtime kinds during a rolling deployment', async () => {
    apiFetch.mockResolvedValue({
      items: [
        { kind: 'track', contentId: 'track-1' },
        { kind: 'podcast-episode', contentId: 'episode-1' },
        { kind: 'album', contentId: 'album-1' },
        { kind: 'ep', contentId: 'ep-1' },
      ],
    })

    const result = await apiRecommendations()

    expect(result.items.map((item) => item.kind)).toEqual(['single', 'podcast', 'album', 'ep'])
  })
})
