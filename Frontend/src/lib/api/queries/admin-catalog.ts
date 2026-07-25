import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { toCatalogList, toCatalogDetail, type PagedCatalogWire, type CatalogDetailWire } from '../mappers'

/** `GET /v1/admin/catalog` — the full catalog list plus filter-chip counts. */
export function catalogQuery() {
  return queryOptions({
    queryKey: ['admin', 'catalog', 'list'],
    queryFn: async () => toCatalogList(await apiFetch<PagedCatalogWire>('/admin/catalog')),
  })
}

/** `GET /v1/admin/catalog/:id` — one release's detail (tracklist, splits, action log). */
export function catalogItemQuery(id: string) {
  return queryOptions({
    queryKey: ['admin', 'catalog', 'detail', id],
    queryFn: async () => toCatalogDetail(await apiFetch<CatalogDetailWire>(`/admin/catalog/${id}`)),
  })
}

/** `POST /v1/admin/catalog/:id/approve` — publish a pending/flagged release. */
export function apiApproveCatalog(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/catalog/${id}/approve`, { method: 'POST' }).then(() => undefined)
}

/** `POST /v1/admin/catalog/:id/flag` — flag for review; note optional. */
export function apiFlagCatalog(id: string, note?: string): Promise<void> {
  return apiFetch<unknown>(`/admin/catalog/${id}/flag`, { method: 'POST', body: note === undefined ? undefined : { note } }).then(() => undefined)
}

/** `POST /v1/admin/catalog/:id/takedown` — reason is required (non-blank). */
export function apiTakedownCatalog(id: string, reason: string): Promise<void> {
  return apiFetch<unknown>(`/admin/catalog/${id}/takedown`, { method: 'POST', body: { reason } }).then(() => undefined)
}
