/**
 * Client-side auth store (mock).
 *
 * Holds the signed-in account + role and persists the session to localStorage.
 * Login/signup are mocked — any well-formed input authenticates — so the UI
 * can demonstrate gating without a backend. Swap the actions for real API
 * calls later; consumers keep using `useAuth()`.
 */

import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'

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
  login: (email: string, password: string) => void
  signup: (name: string, email: string, password: string) => void
  logout: () => void
  becomeArtist: () => void
}

const PERSIST_KEY = 'beatzclik-auth'

/** Default session: signed in as the demo artist so the app opens ready to use. */
const seedAccount: Account = {
  id: 'me',
  name: 'Black Sherif',
  email: 'hello@onepaygh.com',
  avatar: null,
  isArtist: true,
  isAdmin: true,
}

const nameFromEmail = (email: string) =>
  email.split('@')[0].replace(/[._-]+/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase()) || 'Listener'

/** Backfill flags missing from sessions saved before they existed. */
function normalize(a: Partial<Account>): Account {
  const isDemo = a.id === seedAccount.id
  return {
    id: a.id ?? seedAccount.id,
    name: a.name ?? 'Listener',
    email: a.email ?? '',
    avatar: a.avatar ?? null,
    isArtist: a.isArtist ?? isDemo,
    isAdmin: a.isAdmin ?? isDemo,
  }
}

function load(): Account | null {
  try {
    const raw = typeof window !== 'undefined' ? localStorage.getItem(PERSIST_KEY) : null
    if (raw === null) return seedAccount // first visit → signed in
    const parsed = JSON.parse(raw) as Partial<Account> | null
    if (parsed === null) return null // signed out
    return normalize(parsed)
  } catch {
    return seedAccount
  }
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [account, setAccount] = useState<Account | null>(load)
  const first = useRef(true)

  useEffect(() => {
    if (first.current) { first.current = false; return }
    try { localStorage.setItem(PERSIST_KEY, JSON.stringify(account)) } catch { /* ignore */ }
  }, [account])

  const value = useMemo<AuthContextValue>(() => ({
    account,
    isAuthenticated: account !== null,
    // Mock: re-authenticate as the demo artist using the entered email.
    login: (email) => setAccount({ ...seedAccount, email: email.trim() || seedAccount.email }),
    // New signups start as fans; they can upgrade to an artist account.
    signup: (name, email) => setAccount({
      id: `u-${Date.now()}`,
      name: name.trim() || nameFromEmail(email),
      email: email.trim(),
      avatar: null,
      isArtist: false,
      isAdmin: false,
    }),
    logout: () => setAccount(null),
    becomeArtist: () => setAccount((a) => (a ? { ...a, isArtist: true } : a)),
  }), [account])

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
