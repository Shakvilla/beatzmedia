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
  })
}

export function podcastQuery(id: string) {
  return queryOptions({
    queryKey: ['podcasts', 'item', id],
    queryFn: async () => toPodcast(await apiFetch<PodcastWire>(`/podcasts/${id}`)),
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
  })
}
