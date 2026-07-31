import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { toComplianceRequest, type ComplianceRequestWire } from '../mappers'

/**
 * `GET /v1/admin/compliance` — the full request list, unfiltered.
 * The endpoint's `?type=` takes one exact wire value, but the UI's `DSAR` chip covers BOTH
 * `DSAR-export` and `DSAR-delete`, so filtering stays client-side over the whole list.
 */
export function complianceQuery() {
  return queryOptions({
    queryKey: ['admin', 'compliance', 'list'],
    queryFn: async () => (await apiFetch<ComplianceRequestWire[]>('/admin/compliance')).map((c) => toComplianceRequest(c)),
  })
}

/** `POST /v1/admin/compliance/:id/start` — `new|overdue → in_progress`. */
export function apiStartRequest(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/compliance/${encodeURIComponent(id)}/start`, { method: 'POST' }).then(() => undefined)
}

/** `POST /v1/admin/compliance/:id/complete` — `→ completed`. */
export function apiCompleteRequest(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/compliance/${encodeURIComponent(id)}/complete`, { method: 'POST' }).then(() => undefined)
}

/**
 * `POST /v1/admin/compliance/:id/export` — queues a DSAR export. A documented Category-B stub:
 * it mints a job id and audits, but no worker exists and the request's status does not change.
 */
export function apiExportRequest(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/compliance/${encodeURIComponent(id)}/export`, { method: 'POST' }).then(() => undefined)
}

/** `POST /v1/admin/compliance/:id/notice` — records a takedown notice; audit only, no status change. */
export function apiNoticeRequest(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/compliance/${encodeURIComponent(id)}/notice`, { method: 'POST' }).then(() => undefined)
}
