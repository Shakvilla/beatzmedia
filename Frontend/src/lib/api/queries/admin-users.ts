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
export function apiReactivateUser(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/users/${id}/reactivate`, { method: 'POST' }).then(() => undefined)
}
