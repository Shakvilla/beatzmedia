import { queryOptions } from '@tanstack/react-query'
import type { StoreItemType } from '../../../types'
import { apiFetch } from '../client'
import { unwrapPage, type PageWire } from '../pagination'
import { toStoreItem, type StoreItemWire } from '../mappers'

/**
 * Params for `GET /v1/store`. `type` is single-valued on the backend
 * (`StoreResource.list` parses one `StoreItemType` via exact enum-name match —
 * no CSV support), so multi-type tabs (e.g. Hi-Fi = TRACK+ALBUM) must omit
 * `type` and filter client-side, or issue one query per type. `sort` passes
 * through verbatim: the backend's `StoreSort.fromWireValue` accepts exactly
 * `popular|newest|price-asc|price-desc`, matching the frontend's tokens 1:1.
 */
export function storeListQuery(params: { type?: StoreItemType; sort?: string }) {
  const q = new URLSearchParams()
  if (params.type) q.set('type', params.type)
  if (params.sort) q.set('sort', params.sort)
  q.set('page', '1')
  q.set('size', '48')
  return queryOptions({
    queryKey: ['store', 'list', params.type ?? 'all', params.sort ?? 'popular'],
    queryFn: async () =>
      unwrapPage(await apiFetch<PageWire<StoreItemWire>>(`/store?${q.toString()}`), toStoreItem),
  })
}

export function storeItemQuery(id: string) {
  return queryOptions({
    queryKey: ['store', 'item', id],
    queryFn: async () => toStoreItem(await apiFetch<StoreItemWire>(`/store/${id}`)),
  })
}
