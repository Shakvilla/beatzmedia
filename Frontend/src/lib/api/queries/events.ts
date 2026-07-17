import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { unwrapPage, type PageWire } from '../pagination'
import { toEvent, type EventWire } from '../mappers'

/**
 * Params for `GET /v1/events`. Mirrors `EventsResource.list` — `city` and
 * `category` are optional, single-valued filters; `category` must match one
 * of `EventCategory.fromWireValue`'s tokens on the backend.
 */
export function eventsListQuery(params: { city?: string; category?: string } = {}) {
  const q = new URLSearchParams()
  if (params.city) q.set('city', params.city)
  if (params.category) q.set('category', params.category)
  q.set('page', '1')
  q.set('size', '48')
  return queryOptions({
    queryKey: ['events', 'list', params.city ?? 'all', params.category ?? 'all'],
    queryFn: async () =>
      unwrapPage(await apiFetch<PageWire<EventWire>>(`/events?${q.toString()}`), toEvent),
  })
}

export function eventQuery(id: string) {
  return queryOptions({
    queryKey: ['events', 'item', id],
    queryFn: async () => toEvent(await apiFetch<EventWire>(`/events/${id}`)),
  })
}
