import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { toModerationQueue, type ModerationQueueWire } from '../mappers'

/**
 * `GET /v1/admin/moderation` — the moderation queue plus header summary. `status` and `type` are
 * both discrete tabs/chips (no free-text involved), so both are sent server-side.
 */
export function moderationQuery(
  status: 'open' | 'in_review' | 'resolved' | 'all' = 'all',
  type: string = 'all',
) {
  const params = new URLSearchParams({ size: '100' })
  if (status !== 'all') params.set('status', status)
  if (type !== 'all') params.set('type', type)
  return queryOptions({
    queryKey: ['admin', 'moderation', 'queue', status, type],
    queryFn: async () => toModerationQueue(await apiFetch<ModerationQueueWire>(`/admin/moderation?${params}`)),
  })
}

/** `POST /v1/admin/moderation/:id/review` — move a case into review. */
export function apiReviewCase(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/moderation/${id}/review`, { method: 'POST' }).then(() => undefined)
}

/** `POST /v1/admin/moderation/:id/approve` — approve & keep the content. */
export function apiApproveCase(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/moderation/${id}/approve`, { method: 'POST' }).then(() => undefined)
}

/** `POST /v1/admin/moderation/:id/remove` — remove content; reason optional. */
export function apiRemoveCase(id: string, reason?: string): Promise<void> {
  return apiFetch<unknown>(`/admin/moderation/${id}/remove`, { method: 'POST', body: reason === undefined ? undefined : { reason } }).then(() => undefined)
}

/** `POST /v1/admin/moderation/:id/escalate` — escalate to senior review. */
export function apiEscalateCase(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/moderation/${id}/escalate`, { method: 'POST' }).then(() => undefined)
}

/** `POST /v1/admin/moderation/:id/dismiss` — dismiss the report. */
export function apiDismissCase(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/moderation/${id}/dismiss`, { method: 'POST' }).then(() => undefined)
}
