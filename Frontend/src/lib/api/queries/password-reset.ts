import { apiFetch } from '../client'
import { ApiError } from '../errors'

/**
 * Password recovery — both halves.
 *
 * Neither call was reachable before: there was no forgot-password page, the login screen's
 * "Forgot password?" linked back to itself, and the backend had no endpoint to redeem a token
 * even if one had arrived.
 */

/**
 * `POST /v1/me/password/reset` — ask for a reset link.
 *
 * Always resolves, whether or not the address is registered: the backend answers 204 either way so
 * the response cannot be used to discover which emails have accounts. The UI must therefore show
 * the same confirmation in both cases.
 */
export function apiRequestPasswordReset(email: string): Promise<void> {
  return apiFetch<void>('/me/password/reset', { method: 'POST', body: { email } })
}

/** Raised when the reset link is unknown, already used, or expired — the API cannot tell us which. */
export class ResetLinkExpiredError extends Error {}

/** Raised when the chosen password is too short. */
export class WeakPasswordError extends Error {}

/**
 * `POST /v1/auth/password/reset` — redeem the token from the emailed link and set a new password.
 *
 * On `/auth` rather than `/me` because the caller is by definition not signed in.
 *
 * The two failure modes are separated here rather than in the component, because they need
 * different recoveries: an expired link means "request a new one", a weak password means "try
 * again, the link is still good".
 */
export async function apiResetPassword(token: string, password: string): Promise<void> {
  try {
    await apiFetch<void>('/auth/password/reset', { method: 'POST', body: { token, password } })
  } catch (err) {
    if (err instanceof ApiError) {
      if (err.code === 'RESET_TOKEN_INVALID') throw new ResetLinkExpiredError()
      if (err.code === 'WEAK_PASSWORD') throw new WeakPasswordError()
    }
    throw err
  }
}
