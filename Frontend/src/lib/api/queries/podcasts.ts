import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { unwrapPage, type PageWire } from '../pagination'
import {
  toPodcast,
  toPodcastEpisode,
  type PodcastWire,
  type PodcastEpisodeWire,
} from '../mappers'

/**
 * Params for `GET /v1/podcasts`. Mirrors `PodcastResource.listPodcasts` — `category`
 * is an optional, single-valued filter that must match one of
 * `PodcastCategory.fromWireValue`'s tokens on the backend.
 */
export function podcastsListQuery(params: { category?: string } = {}) {
  const q = new URLSearchParams()
  if (params.category) q.set('category', params.category)
  q.set('page', '1')
  q.set('size', '48')
  return queryOptions({
    queryKey: ['podcasts', 'list', params.category ?? 'all'],
    queryFn: async () =>
      unwrapPage(await apiFetch<PageWire<PodcastWire>>(`/podcasts?${q.toString()}`), toPodcast),
    // A new Studio upload appears only after asynchronous audio processing and projection.
    // Keep active browse pages fresh while that work completes.
    staleTime: 0,
    refetchInterval: 30_000,
    refetchOnWindowFocus: 'always',
  })
}

export function podcastQuery(id: string) {
  return queryOptions({
    queryKey: ['podcasts', 'item', id],
    queryFn: async () => toPodcast(await apiFetch<PodcastWire>(`/podcasts/${id}`)),
    staleTime: 0,
    refetchInterval: 30_000,
    refetchOnWindowFocus: 'always',
  })
}

/**
 * `GET /v1/podcasts/:id/episodes` returns a bare `PodcastEpisodeView[]` — not a
 * `Page<T>` — so this maps the array directly (no `unwrapPage`).
 */
export function podcastEpisodesQuery(id: string) {
  return queryOptions({
    queryKey: ['podcasts', 'item', id, 'episodes'],
    queryFn: async () =>
      (await apiFetch<PodcastEpisodeWire[]>(`/podcasts/${id}/episodes`)).map(toPodcastEpisode),
    staleTime: 0,
    refetchInterval: 30_000,
    refetchOnWindowFocus: 'always',
  })
}

export interface EpisodeStreamWire {
  audioUrl: string
  previewSeconds: number | null
  expiresAt: string | null
}

/** Signed audio URL for a podcast episode. Episodes have their own stream resource. */
export function episodeStreamQuery(episodeId: string) {
  return queryOptions({
    queryKey: ['podcasts', 'episode', episodeId, 'stream'],
    queryFn: async () =>
      apiFetch<EpisodeStreamWire>(`/podcasts/episodes/${encodeURIComponent(episodeId)}/stream`),
    retry: false,
    staleTime: 0,
    gcTime: 60_000,
    refetchOnWindowFocus: false,
  })
}
