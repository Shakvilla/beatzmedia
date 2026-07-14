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
  const hydrated = useRef(false)

  useEffect(() => {
    setUnauthorizedHandler(() => setAccount(null))
  }, [])

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
  }), [account, isLoading])

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
