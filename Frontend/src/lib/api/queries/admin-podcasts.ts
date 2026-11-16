import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import type { PageWire } from '../pagination'

export interface AdminPodcastEpisode {
  id: string
  showId: string
  showTitle: string
  title: string
  artistId: string
  status: 'draft' | 'scheduled' | 'processing' | 'published'
  durationSec: number
  createdAt: string
}

export function adminPodcastEpisodesQuery(page: number) {
  return queryOptions({
    queryKey: ['admin', 'podcasts', 'episodes', page],
    queryFn: () => apiFetch<PageWire<AdminPodcastEpisode>>(`/admin/podcasts/episodes?page=${page}&size=50`),
    staleTime: 0,
    refetchInterval: 15_000,
  })
}
