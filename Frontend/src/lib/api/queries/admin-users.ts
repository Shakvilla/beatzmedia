import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import {
  toUsersList, toUserDetail,
  type PagedUsersWire, type UserDetailWire,
} from '../mappers'

/** `GET /v1/admin/users` — the full admin user list plus the filter-pill counts. */
export function usersQuery() {
  return queryOptions({
    queryKey: ['admin', 'users', 'list'],
    queryFn: async () => toUsersList(await apiFetch<PagedUsersWire>('/admin/users')),
  })
}

/** `GET /v1/admin/users/:id` — one user's header summary + server action log. */
export function userDetailQuery(id: string) {
  return queryOptions({
    queryKey: ['admin', 'users', 'detail', id],
    queryFn: async () => toUserDetail(await apiFetch<UserDetailWire>(`/admin/users/${id}`)),
  })
}

/** `POST /v1/admin/users/:id/verify` — mark an artist verified. */
export function apiVerifyUser(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/users/${id}/verify`, { method: 'POST' }).then(() => undefined)
}

/** `POST /v1/admin/users/:id/suspend { reason }` — reason is required (non-blank). */
export function apiSuspendUser(id: string, reason: string): Promise<void> {
  return apiFetch<unknown>(`/admin/users/${id}/suspend`, { method: 'POST', body: { reason } }).then(() => undefined)
}

/** `POST /v1/admin/users/:id/reactivate` — lift a suspension. */
/**
 * `POST /v1/admin/users/:id/data-export` — start a GDPR/DSAR export. 202 Accepted.
 *
 * The endpoint has existed and been guarded (`super-admin`, `support`) since the module was built.
 * The menu item that should have called it only raised a toast reading "Preparing data export",
 * so a compliance request could be marked handled with nothing behind it.
 */
export function apiExportUserData(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/users/${id}/data-export`, { method: 'POST' }).then(() => undefined)
}

/** `POST /v1/admin/users/:id/impersonate` — mint a short-lived impersonation token (super-admin). */
/**
 * `POST /admin/users/:id/impersonate` — mints a short-lived, scoped token for the target account.
 * Super-admin only; the backend audits the target and the expiry, never the token itself.
 *
 * The response carries `expiresAt` and `scopes` too; the previous type declared only `token`, so
 * the expiry the banner needs was being discarded at the type boundary (GAP-08).
 */
export function apiImpersonateUser(id: string): Promise<ImpersonationToken> {
  return apiFetch<ImpersonationToken>(`/admin/users/${id}/impersonate`, { method: 'POST' })
}

export interface ImpersonationToken {
  token: string
  /** ISO-8601 instant at which the token stops being accepted. */
  expiresAt: string
  scopes: string[]
}

export function apiReactivateUser(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/users/${id}/reactivate`, { method: 'POST' }).then(() => undefined)
}
