import type { StoreItem } from '../../types'

/**
 * Client-side text filter for the store header's search box.
 *
 * `GET /v1/store` has no free-text search param (StoreResource only accepts
 * `type`/`genre`/`sort`/`page`/`size`), so the `q` search param is applied
 * over the already-fetched page here instead of being sent to the backend.
 */
export function filterByQuery(items: StoreItem[], q: string | undefined): StoreItem[] {
  if (!q) return items
  const needle = q.toLowerCase()
  return items.filter((item) => `${item.title} ${item.artistName}`.toLowerCase().includes(needle))
}
