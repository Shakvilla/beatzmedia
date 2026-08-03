import { useQuery } from '@tanstack/react-query'
import type { AdminRole } from '../../lib/admin-data'
import { adminTeamQuery } from '../../lib/api/queries/admin-team'
import { initialsOf } from '../../utils/initials'
import { useAuth } from '../auth/auth-context'

export interface AdminIdentity {
  /** The signed-in admin's own name, falling back to the account name. May be ''. */
  name: string
  initials: string
  /**
   * The admin's ACTUAL role, or null when it isn't known. Callers must render nothing
   * rather than guessing — the console previously showed a hardcoded SUPER-ADMIN badge to
   * every admin, including support and moderator members.
   */
  role: AdminRole | null
}

/**
 * The signed-in admin's identity for the admin shell.
 *
 * Replaces the `adminUser` mock constant, which hard-coded "Yaa" / "Super-admin" / "AD" and
 * so misreported both the name and the privilege level of everyone who signed in.
 *
 * `GET /v1/admin/team` is matched on email: its `id` is the admin_member id
 * (e.g. `adm-qa-019fb8a6`), not the account id, so it cannot be matched on the JWT subject.
 */
export function useAdminIdentity(): AdminIdentity {
  const { account } = useAuth()
  const { data: team } = useQuery(adminTeamQuery())

  const email = account?.email?.trim().toLowerCase()
  const me = email ? team?.find((m) => m.email.trim().toLowerCase() === email) : undefined

  const name = me?.name.trim() || account?.name?.trim() || ''
  return { name, initials: initialsOf(name), role: me?.role ?? null }
}
