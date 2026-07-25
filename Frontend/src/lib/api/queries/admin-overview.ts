import { keepPreviousData, queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import {
  toAdminOverview, toHealth, toAuditPage,
  type AdminOverviewWire, type HealthWire, type AuditPageWire,
} from '../mappers'

/** Rows per audit page — matches the shared paginator's default so the control looks unchanged. */
export const AUDIT_PAGE_SIZE = 8

/**
 * `GET /v1/admin/overview?range=` — dashboard KPIs, GMV series, and top artists.
 * An unrecognised range is a 422 server-side, so only the three UI range keys are ever sent.
 */
export function overviewQuery(range: string) {
  return queryOptions({
    queryKey: ['admin', 'overview', range],
    queryFn: async () => toAdminOverview(await apiFetch<AdminOverviewWire>(`/admin/overview?range=${range}`)),
    placeholderData: keepPreviousData,
  })
}

/**
 * `GET /v1/admin/health` — currently a hardcoded honest-empty payload (no APM/incident/telemetry
 * subsystem exists yet), so this reliably returns `normal` with three empty arrays.
 */
export function healthQuery() {
  return queryOptions({
    queryKey: ['admin', 'health'],
    queryFn: async () => toHealth(await apiFetch<HealthWire>('/admin/health')),
  })
}

/**
 * `GET /v1/admin/audit` — one server-paged slice of the append-only audit log.
 * `super-admin` only. `type`/`q` are sent only when set; `q` matches action/target server-side
 * (NOT actor — the single search box drives `q`).
 */
export function auditQuery(type: string, q: string, page: number) {
  const params = new URLSearchParams({ page: String(page), size: String(AUDIT_PAGE_SIZE) })
  if (type && type !== 'all') params.set('type', type)
  if (q) params.set('q', q)
  return queryOptions({
    queryKey: ['admin', 'audit', type, q, page],
    queryFn: async () => toAuditPage(await apiFetch<AuditPageWire>(`/admin/audit?${params}`)),
    placeholderData: keepPreviousData,
  })
}
