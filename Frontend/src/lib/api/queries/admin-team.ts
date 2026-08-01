import { queryOptions } from '@tanstack/react-query'
import type { AdminMember, AdminRole } from '../../admin-data'
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
    lastActive: w.lastActive ?? '',
  }
}

/** `GET /v1/admin/team` — the admin members. Any signed-in admin may read it. */
export function adminTeamQuery() {
  return queryOptions({
    queryKey: ['admin', 'team'],
    queryFn: async () => (await apiFetch<AdminMemberWire[]>('/admin/team')).map(toAdminMember),
  })
}
