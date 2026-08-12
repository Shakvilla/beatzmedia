/**
 * Client-side auth store.
 *
 * Holds the signed-in account + role, backed by the real /v1/auth and /v1/me
 * endpoints. The JWT returned by login/signup is persisted via
 * `lib/api/token.ts`; the session hydrates from GET /v1/me on load.
 */

import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { apiFetch, setUnauthorizedHandler } from '../../lib/api/client'
import { clearToken, getToken, setToken } from '../../lib/api/token'
import {
  beginImpersonation,
  currentImpersonation,
  endImpersonation,
  hasImpersonationMarker,
  type Impersonation,
} from './impersonation'

export interface Account {
  id: string
  name: string
  email: string
  avatar: string | null
  isArtist: boolean
  isAdmin: boolean
}

interface AuthContextValue {
  account: Account | null
  isAuthenticated: boolean
  /** True until the initial session hydration (GET /v1/me) has resolved. */
  isLoading: boolean
  login: (email: string, password: string) => Promise<void>
  signup: (name: string, email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  becomeArtist: () => Promise<void>
  /**
   * The impersonation in progress, or null (GAP-08). Present so every surface — not just the admin
   * console — can show who the operator is acting as.
   */
  impersonation: Impersonation | null
  /** Swaps the session to `token` and re-hydrates as the target account. */
  startImpersonation: (token: string, subject: Impersonation) => Promise<void>
  /** Restores the operator's own session. Safe to call when not impersonating. */
  stopImpersonation: () => Promise<void>
}

interface AuthResponse {
  token: string
  account: Account
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [account, setAccount] = useState<Account | null>(null)
  // Start "loading" only when there's a token to verify; a signed-out visitor is
  // resolved immediately (no synchronous setState in the hydration effect below).
  const [isLoading, setIsLoading] = useState(() => getToken() !== null)
  const [impersonation, setImpersonation] = useState<Impersonation | null>(() => currentImpersonation())
  const hydrated = useRef(false)

  useEffect(() => {
    /*
      A 401 while impersonating means the short-lived token lapsed. Drop back to the operator's own
      session rather than signing them out: they were mid-investigation, and the console session is
      still valid. Without this the expiry would bounce them to /login.
    */
    setUnauthorizedHandler(() => {
      if (hasImpersonationMarker()) {
        endImpersonation()
        setImpersonation(null)
        apiFetch<Account>('/me').then(setAccount).catch(() => setAccount(null))
        return
      }
      setAccount(null)
    })
  }, [])

  /*
    The token is time-boxed, so the banner's countdown has to end in something. Exiting on our own
    schedule returns the operator to the console deliberately, instead of leaving them to discover
    the expiry through the first request that fails.
  */
  useEffect(() => {
    if (!impersonation) return
    const msLeft = Date.parse(impersonation.expiresAt) - Date.now()
    if (msLeft <= 0) return
    const timer = setTimeout(() => {
      endImpersonation()
      setImpersonation(null)
      apiFetch<Account>('/me').then(setAccount).catch(() => setAccount(null))
    }, msLeft)
    return () => clearTimeout(timer)
  }, [impersonation])

  useEffect(() => {
    if (hydrated.current) return
    hydrated.current = true
    if (getToken() === null) return // no session to hydrate; isLoading already false
    apiFetch<Account>('/me')
      .then(setAccount)
      .catch(() => {
        clearToken()
        setAccount(null)
      })
      .finally(() => setIsLoading(false))
  }, [])

  const value = useMemo<AuthContextValue>(() => ({
    account,
    isAuthenticated: account !== null,
    isLoading,
    login: async (email, password) => {
      const result = await apiFetch<AuthResponse>('/auth/login', {
        method: 'POST',
        body: { email, password },
      })
      setToken(result.token)
      setAccount(result.account)
    },
    signup: async (name, email, password) => {
      const result = await apiFetch<AuthResponse>('/auth/signup', {
        method: 'POST',
        body: { name, email, password },
      })
      setToken(result.token)
      setAccount(result.account)
    },
    logout: async () => {
      try {
        await apiFetch('/auth/logout', { method: 'POST' })
      } finally {
        clearToken()
        setAccount(null)
      }
    },
    becomeArtist: async () => {
      const result = await apiFetch<Account>('/me/become-artist', { method: 'POST' })
      setAccount(result)
    },
    impersonation,
    startImpersonation: async (token, subject) => {
      beginImpersonation(token, subject)
      setImpersonation(subject)
      // Re-hydrate rather than trusting the admin's row: /me under the new token is the only
      // statement of who the session actually is now.
      const me = await apiFetch<Account>('/me')
      setAccount(me)
    },
    stopImpersonation: async () => {
      const restored = endImpersonation()
      setImpersonation(null)
      if (!restored) {
        setAccount(null)
        return
      }
      const me = await apiFetch<Account>('/me')
      setAccount(me)
    },
  }), [account, isLoading, impersonation])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within an <AuthProvider>')
  return ctx
}

export function initialsOfAccount(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (!parts.length) return '?'
  return (parts[0][0] + (parts[1]?.[0] ?? '')).toUpperCase()
}
