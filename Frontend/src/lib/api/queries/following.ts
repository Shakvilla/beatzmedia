import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'

export type FollowingFeedKind = 'release' | 'podcast-episode'

export interface FollowingFeedItem {
  kind: FollowingFeedKind
  contentId: string
  creatorId: string
  title: string
  subtitle: string
  image: string | null
  route: string
  publishedAt: string
}

export interface FollowingFeedPage {
  items: FollowingFeedItem[]
  nextCursor: string | null
}

export function apiFollowingFeed(cursor: string | null = null, limit = 20): Promise<FollowingFeedPage> {
  const params = new URLSearchParams({ limit: String(limit) })
  if (cursor) params.set('cursor', cursor)
  return apiFetch<FollowingFeedPage>(`/me/following?${params}`)
}

export function followingFeedQuery(enabled: boolean, limit = 6) {
  return queryOptions({
    queryKey: ['me', 'following', limit],
    queryFn: () => apiFollowingFeed(null, limit),
    enabled,
  })
}
