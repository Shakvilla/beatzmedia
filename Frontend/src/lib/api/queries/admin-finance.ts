import { keepPreviousData, queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import {
  toFinanceOverview, toLedgerPage, toPendingPayout, toDisputeDetail,
  type FinanceOverviewWire, type LedgerPageWire, type PendingPayoutWire, type DisputeDetailWire,
  type DisputeDetail,
} from '../mappers'

/** Rows per ledger page — matches the shared paginator's default so the control looks unchanged. */
export const LEDGER_PAGE_SIZE = 8

/**
 * `GET /v1/admin/finance` — KPIs, pending payouts, provider mix, and open disputes.
 * No `range` param is sent: this screen has no range control, so the server's `7d` default applies.
 */
export function financeOverviewQuery() {
  return queryOptions({
    queryKey: ['admin', 'finance', 'overview'],
    queryFn: async () => toFinanceOverview(await apiFetch<FinanceOverviewWire>('/admin/finance')),
  })
}

/**
 * `GET /v1/admin/finance/ledger` — one server-paged slice of the book. `type`/`q` are sent only
 * when set; the server treats a blank/unknown `type` as "no filter" rather than a 422.
 */
export function ledgerQuery(type: string, q: string, page: number) {
  const params = new URLSearchParams({ page: String(page), size: String(LEDGER_PAGE_SIZE) })
  if (type && type !== 'all') params.set('type', type)
  if (q) params.set('q', q)
  return queryOptions({
    queryKey: ['admin', 'finance', 'ledger', type, q, page],
    queryFn: async () => toLedgerPage(await apiFetch<LedgerPageWire>(`/admin/finance/ledger?${params}`)),
    placeholderData: keepPreviousData,
  })
}

/** `GET /v1/admin/finance/disputes/:id` — one dispute with its server-side timeline. */
export function disputeQuery(id: string) {
  return queryOptions({
    queryKey: ['admin', 'finance', 'dispute', id],
    queryFn: async () => toDisputeDetail(await apiFetch<DisputeDetailWire>(`/admin/finance/disputes/${encodeURIComponent(id)}`)),
  })
}

/** `GET /v1/admin/finance/payouts` — payable withdrawals (`ready` and `kyc_pending` only). */
export function pendingPayoutsQuery() {
  return queryOptions({
    queryKey: ['admin', 'finance', 'payouts'],
    queryFn: async () => (await apiFetch<PendingPayoutWire[]>('/admin/finance/payouts')).map(toPendingPayout),
  })
}

/**
 * `POST /v1/admin/finance/disputes/:id/refund` — a money POST, so an `Idempotency-Key` is REQUIRED
 * (a blank one is a 400). `amount` is omitted, which the backend treats as a FULL refund.
 *
 * Returns the authoritative post-action view. The server treats a refund on a non-open dispute as a
 * BENIGN NO-OP with HTTP 200 (not an error), so "did not throw" does NOT mean money moved — the
 * caller MUST check the returned `status`.
 */
export function apiRefundDispute(id: string, reason: string): Promise<DisputeDetail> {
  return apiFetch<DisputeDetailWire>(`/admin/finance/disputes/${encodeURIComponent(id)}/refund`, {
    method: 'POST',
    body: { reason },
    idempotencyKey: crypto.randomUUID(),
  }).then((w) => toDisputeDetail(w))
}

/** `POST /v1/admin/finance/disputes/:id/reject` — `reason` is required (non-blank). */
export function apiRejectDispute(id: string, reason: string): Promise<void> {
  return apiFetch<unknown>(`/admin/finance/disputes/${encodeURIComponent(id)}/reject`, { method: 'POST', body: { reason } }).then(() => undefined)
}

/** `POST /v1/admin/finance/disputes/:id/escalate` — raises to senior finance; stays open. */
export function apiEscalateDispute(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/finance/disputes/${encodeURIComponent(id)}/escalate`, { method: 'POST' }).then(() => undefined)
}

/**
 * `POST /v1/admin/finance/payouts/run-weekly` — pays every ready, KYC-verified withdrawal.
 * Money POST: `Idempotency-Key` REQUIRED. The server's per-withdrawal exactly-once guard means a
 * retry cannot double-pay.
 *
 * Returns the server's authoritative paid `count`: the run skips KYC-unverified creators and
 * isolates per-withdrawal failures, so the number actually paid can be lower than the number of
 * `ready` rows the client was showing. The caller must report THIS count, never its own guess.
 */
export function apiRunWeeklyPayouts(): Promise<{ count: number }> {
  return apiFetch<{ count: number }>('/admin/finance/payouts/run-weekly', {
    method: 'POST',
    idempotencyKey: crypto.randomUUID(),
  }).then((batch) => ({ count: batch?.count ?? 0 }))
}

/**
 * `POST /v1/admin/finance/payouts/:id/send` — sends one payout (`id` is the withdrawal id).
 * Money POST: `Idempotency-Key` REQUIRED. Blocks with 409 when the artist's KYC is unverified.
 */
export function apiSendPayout(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/finance/payouts/${encodeURIComponent(id)}/send`, {
    method: 'POST',
    idempotencyKey: crypto.randomUUID(),
  }).then(() => undefined)
}
