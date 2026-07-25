import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { toModerationQueue, type ModerationQueueWire } from '../mappers'

/** `GET /v1/admin/moderation` — the moderation queue plus header summary. */
export function moderationQuery() {
  return queryOptions({
    queryKey: ['admin', 'moderation', 'queue'],
    queryFn: async () => toModerationQueue(await apiFetch<ModerationQueueWire>('/admin/moderation')),
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
