import { queryOptions } from '@tanstack/react-query'
import type { AdminMember, AdminRole } from '../../admin-data'
import { relativeTimeAgo } from '../../format'
import { apiFetch } from '../client'


export interface AdminMemberWire {
  id: string
  name: string | null
  email: string | null
  role: string | null
  lastActive: string | null
}

const ROLES: AdminRole[] = ['Super-admin', 'Finance', 'Moderator', 'Editor', 'Support']

/** Wire roles are kebab-case (`super-admin`); the UI labels them `Super-admin`. */
export function toAdminRole(wire: string | null): AdminRole | null {
  if (!wire) return null
  const label = wire.charAt(0).toUpperCase() + wire.slice(1)
  return ROLES.find((r) => r.toLowerCase() === label.toLowerCase()) ?? null
}

export function toAdminMember(w: AdminMemberWire): AdminMember {
  return {
    id: w.id,
    name: w.name ?? '',
    email: w.email ?? '',
    role: toAdminRole(w.role) ?? 'Support',
    // The API sends an ISO instant; the row renders this string verbatim beside the email. Left
    // unformatted it read "2026-08-03T20:08:13.480788Z" in the console. An invited admin who has
    // never signed in has no lastActive at all, which is "never", not the epoch.
    lastActive: w.lastActive ? relativeTimeAgo(w.lastActive) : 'never',
  }
}

/** `GET /v1/admin/team` — the admin members. Any signed-in admin may read it. */
export function adminTeamQuery() {
  return queryOptions({
    queryKey: ['admin', 'team'],
    queryFn: async () => (await apiFetch<AdminMemberWire[]>('/admin/team')).map(toAdminMember),
  })
}

/** UI label (`Super-admin`) → the kebab-case value the API expects (`super-admin`). */
export function toWireRole(role: AdminRole): string {
  return role.toLowerCase()
}

/**
 * The three team mutations. All are super-admin only and all already existed on the backend
 * (`AdminTeamResource`) — the settings page just never called them, mutating local React state
 * instead and telling the operator "team management has no backend".
 */

/** `POST /v1/admin/team/invite` — 201 with the new member. */
export async function apiInviteAdmin(email: string, role: AdminRole): Promise<AdminMember> {
  return toAdminMember(
    await apiFetch<AdminMemberWire>('/admin/team/invite', {
      method: 'POST',
      body: JSON.stringify({ email, role: toWireRole(role) }),
    }),
  )
}

/** `PATCH /v1/admin/team/{id}` — 200 with the updated member. */
export async function apiChangeAdminRole(id: string, role: AdminRole): Promise<AdminMember> {
  return toAdminMember(
    await apiFetch<AdminMemberWire>(`/admin/team/${id}`, {
      method: 'PATCH',
      body: JSON.stringify({ role: toWireRole(role) }),
    }),
  )
}

/** `DELETE /v1/admin/team/{id}` — 204, no body. */
export async function apiRemoveAdmin(id: string): Promise<void> {
  await apiFetch<void>(`/admin/team/${id}`, { method: 'DELETE' })
}
