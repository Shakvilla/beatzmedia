import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from '../client'
import { apiFollowingFeed, followingFeedQuery } from './following'

vi.mock('../client')

beforeEach(() => vi.resetAllMocks())

describe('following feed API', () => {
  it('requests a bounded first page', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ items: [], nextCursor: null })
    await apiFollowingFeed(null, 12)
    expect(apiFetch).toHaveBeenCalledWith('/me/following?limit=12')
  })

  it('encodes cursor pagination and gates signed-out queries', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ items: [], nextCursor: null })
    await apiFollowingFeed('a+b/c=', 20)
    expect(apiFetch).toHaveBeenCalledWith('/me/following?limit=20&cursor=a%2Bb%2Fc%3D')
    expect(followingFeedQuery(false).enabled).toBe(false)
    expect(followingFeedQuery(true).queryKey).toEqual(['me', 'following', 6])
  })
})
