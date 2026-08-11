import { clearToken, getToken, setToken } from '../../lib/api/token'

/**
 * Session swap for admin impersonation (GAP-08).
 *
 * The console could already mint an impersonation token — `POST /admin/users/:id/impersonate` is
 * real, super-admin only, and audited — but the UI only reported that a token had been *issued*.
 * That was the honest half: the operator could not actually use it, because nothing applied it to
 * the browser session.
 *
 * <p><strong>The token is never rendered.</strong> It is a live bearer credential with `fan`/
 * `artist` scopes. The backend service is careful never to write it to the audit log; putting it on
 * screen — even behind a copy button — would undo that care, since anything on screen can be
 * shoulder-surfed, screenshotted, or pasted somewhere it outlives its TTL. It goes straight into the
 * session and is read back only by the API client.
 *
 * <p><strong>The operator's own token is stashed, not discarded</strong>, so leaving impersonation
 * returns them to the console rather than to a login screen. It is held in the same `localStorage`
 * the live token already occupies: no new exposure surface, but worth stating plainly rather than
 * leaving a reader to work out.
 *
 * <p><strong>Expiry is the server's job.</strong> The token is time-boxed and the API will reject it
 * once it lapses; `expiresAt` here drives the countdown and the automatic exit, so the operator is
 * returned to the console deliberately instead of being bounced to `/login` by a 401.
 */

const ADMIN_TOKEN_KEY = 'beatzclik-admin-token'
const IMPERSONATION_KEY = 'beatzclik-impersonation'

export interface Impersonation {
  subjectId: string
  subjectName: string
  /** ISO-8601. The token's own expiry, as reported by the server. */
  expiresAt: string
}

function read<T>(key: string): T | null {
  try {
    const raw = localStorage.getItem(key)
    return raw ? (JSON.parse(raw) as T) : null
  } catch {
    return null
  }
}

/** The impersonation in progress, or null. Returns null once `expiresAt` has passed. */
export function currentImpersonation(now: number = Date.now()): Impersonation | null {
  const value = read<Impersonation>(IMPERSONATION_KEY)
  if (!value) return null
  const expiry = Date.parse(value.expiresAt)
  // An unparseable expiry is treated as expired: better to drop back to the console than to leave
  // an operator in a session whose lifetime nothing can reason about.
  if (!Number.isFinite(expiry) || expiry <= now) return null
  return value
}

/** True when an impersonation marker exists at all, expired or not. */
export function hasImpersonationMarker(): boolean {
  return read<Impersonation>(IMPERSONATION_KEY) !== null
}

/**
 * Swaps the session to the impersonation token, stashing the operator's own.
 *
 * @throws when there is no current session to return to — impersonating from a signed-out state
 *     would strand the operator, and it should be impossible to reach anyway.
 */
export function beginImpersonation(token: string, subject: Impersonation): void {
  const adminToken = getToken()
  if (!adminToken) throw new Error('Cannot impersonate without a signed-in session')
  try {
    localStorage.setItem(ADMIN_TOKEN_KEY, adminToken)
    localStorage.setItem(IMPERSONATION_KEY, JSON.stringify(subject))
  } catch {
    throw new Error('Session storage is unavailable, so impersonation cannot be exited safely')
  }
  setToken(token)
}

/**
 * Restores the operator's own session and clears the markers.
 *
 * @returns true when an admin token was restored; false when there was nothing to restore, in which
 *     case the session is cleared rather than left holding an impersonation token.
 */
export function endImpersonation(): boolean {
  let restored = false
  try {
    const adminToken = localStorage.getItem(ADMIN_TOKEN_KEY)
    if (adminToken) {
      setToken(adminToken)
      restored = true
    } else {
      clearToken()
    }
    localStorage.removeItem(ADMIN_TOKEN_KEY)
    localStorage.removeItem(IMPERSONATION_KEY)
  } catch {
    clearToken()
  }
  return restored
}
