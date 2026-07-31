import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { toRiskBoard, type RiskBoardWire } from '../mappers'

/** `GET /v1/admin/risk` — the risk KPI strip and open signals. `moderator` or `super-admin`. */
export function riskBoardQuery() {
  return queryOptions({
    queryKey: ['admin', 'risk', 'board'],
    queryFn: async () => toRiskBoard(await apiFetch<RiskBoardWire>('/admin/risk')),
  })
}

/** `POST /v1/admin/risk/:id/review` — logs a review; deliberately does NOT change the status. */
export function apiReviewSignal(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/risk/${encodeURIComponent(id)}/review`, { method: 'POST' }).then(() => undefined)
}

/** `POST /v1/admin/risk/:id/clear` — `open → cleared`. */
export function apiClearSignal(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/risk/${encodeURIComponent(id)}/clear`, { method: 'POST' }).then(() => undefined)
}

/**
 * `POST /v1/admin/risk/:id/ban` — `open → banned`, and bans the subject's account.
 * `reason` is `@NotBlank` server-side (422 otherwise) and is what lands in the audit trail.
 */
export function apiBanSignal(id: string, reason: string): Promise<void> {
  return apiFetch<unknown>(`/admin/risk/${encodeURIComponent(id)}/ban`, { method: 'POST', body: { reason } }).then(() => undefined)
}
