import { beforeEach, describe, expect, it } from 'vitest'
import {
  beginImpersonation,
  currentImpersonation,
  endImpersonation,
  hasImpersonationMarker,
} from './impersonation'
import { clearToken, getToken, setToken } from '../../lib/api/token'

/**
 * The session swap behind admin impersonation (GAP-08).
 *
 * The stash-and-restore is the load-bearing part: if the operator's own token is not put back, they
 * are stranded in someone else's session with no way to the console except signing in again. These
 * cover that both ways round, plus the expiry rule that decides when the banner stops trusting a
 * session.
 */

const SUBJECT = {
  subjectId: 'u-123',
  subjectName: 'Ama Boateng',
  expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
}

describe('impersonation session swap', () => {
  beforeEach(() => {
    localStorage.clear()
    clearToken()
  })

  it('swaps the active token and stashes the operator’s own', () => {
    setToken('admin-token')

    beginImpersonation('impersonation-token', SUBJECT)

    expect(getToken()).toBe('impersonation-token')
    expect(currentImpersonation()?.subjectName).toBe('Ama Boateng')
  })

  it('restores the operator’s token on exit', () => {
    setToken('admin-token')
    beginImpersonation('impersonation-token', SUBJECT)

    const restored = endImpersonation()

    expect(restored).toBe(true)
    expect(getToken()).toBe('admin-token')
    expect(currentImpersonation()).toBeNull()
    expect(hasImpersonationMarker()).toBe(false)
  })

  /** Impersonating from a signed-out state would strand the operator with nothing to return to. */
  it('refuses to start without a session to return to', () => {
    expect(() => beginImpersonation('impersonation-token', SUBJECT)).toThrow(/without a signed-in session/)
    expect(getToken()).toBeNull()
  })

  /**
   * If the stash is somehow missing, exit must not leave the impersonation token in place — that
   * would look like a normal session while actually being someone else's.
   */
  it('clears the session rather than keeping the impersonation token when there is nothing to restore', () => {
    setToken('admin-token')
    beginImpersonation('impersonation-token', SUBJECT)
    localStorage.removeItem('beatzclik-admin-token')

    const restored = endImpersonation()

    expect(restored).toBe(false)
    expect(getToken()).toBeNull()
  })

  it('reports no impersonation once the token has expired', () => {
    setToken('admin-token')
    beginImpersonation('impersonation-token', {
      ...SUBJECT,
      expiresAt: new Date(Date.now() - 1_000).toISOString(),
    })

    expect(currentImpersonation()).toBeNull()
    // The marker survives so the app knows to restore the operator rather than sign them out.
    expect(hasImpersonationMarker()).toBe(true)
  })

  /**
   * An expiry nothing can parse is treated as expired. Leaving an operator in a session whose
   * lifetime cannot be reasoned about is the worse failure — the banner would count down from NaN
   * and never end.
   */
  it('treats an unparseable expiry as expired', () => {
    setToken('admin-token')
    beginImpersonation('impersonation-token', { ...SUBJECT, expiresAt: 'not-a-date' })

    expect(currentImpersonation()).toBeNull()
  })

  it('exit is safe to call when not impersonating', () => {
    setToken('admin-token')

    expect(() => endImpersonation()).not.toThrow()
    expect(getToken()).toBeNull()
  })
})
