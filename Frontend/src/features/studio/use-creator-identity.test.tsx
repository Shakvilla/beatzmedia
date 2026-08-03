import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useCreatorIdentity, initialsOf } from './use-creator-identity'
import * as auth from '../auth/auth-context'
import * as client from '../../lib/api/client'

vi.mock('../auth/auth-context')
vi.mock('../../lib/api/client')

const ACCOUNT = { id: 'acc-77', name: 'Ama Serwaa', email: 'a@b.c', avatar: null, isArtist: true, isAdmin: false }

function mockAuth(account: typeof ACCOUNT | null) {
  vi.mocked(auth.useAuth).mockReturnValue({
    account, isAuthenticated: account !== null, isLoading: false,
    login: vi.fn(), signup: vi.fn(), logout: vi.fn(), becomeArtist: vi.fn(),
  } as unknown as ReturnType<typeof auth.useAuth>)
}

/** Routes each apiFetch by path so profile and artist can be varied independently. */
function mockFetch(routes: Record<string, unknown | Error>) {
  vi.mocked(client.apiFetch).mockImplementation((path: string) => {
    const key = Object.keys(routes).find((k) => path.startsWith(k))
    if (key === undefined) return Promise.reject(new Error(`unrouted ${path}`))
    const value = routes[key]
    return value instanceof Error ? Promise.reject(value) : Promise.resolve(value)
  })
}

function wrapperWith() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: ReactNode }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

beforeEach(() => vi.resetAllMocks())

describe('initialsOf', () => {
  it('takes the first letter of the first two words, uppercased', () => {
    expect(initialsOf('ama serwaa')).toBe('AS')
    expect(initialsOf('Kwesi')).toBe('K')
    expect(initialsOf('  Ama   Serwaa  Mensah ')).toBe('AS')
  })

  it('returns ? for an empty name rather than throwing', () => {
    expect(initialsOf('')).toBe('?')
    expect(initialsOf('   ')).toBe('?')
  })
})

describe('useCreatorIdentity', () => {
  it('exposes the signed-in account id as the artist id', async () => {
    mockAuth(ACCOUNT)
    mockFetch({ '/studio/profile': { displayName: 'Ama S' }, '/artists/': { id: 'acc-77', verified: true } })
    const { result } = renderHook(() => useCreatorIdentity(), { wrapper: wrapperWith() })

    expect(result.current.id).toBe('acc-77')
  })

  it('prefers the studio profile display name over the account name', async () => {
    mockAuth(ACCOUNT)
    mockFetch({ '/studio/profile': { displayName: 'Ama S' }, '/artists/': { id: 'acc-77', verified: false } })
    const { result } = renderHook(() => useCreatorIdentity(), { wrapper: wrapperWith() })

    await waitFor(() => expect(result.current.name).toBe('Ama S'))
    expect(result.current.initials).toBe('AS')
  })

  it('falls back to the account name when the profile has no display name', async () => {
    mockAuth(ACCOUNT)
    mockFetch({ '/studio/profile': { displayName: '   ' }, '/artists/': { id: 'acc-77', verified: false } })
    const { result } = renderHook(() => useCreatorIdentity(), { wrapper: wrapperWith() })

    await waitFor(() => expect(result.current.name).toBe('Ama Serwaa'))
  })

  it('reports verified only when the public artist profile says so', async () => {
    mockAuth(ACCOUNT)
    mockFetch({ '/studio/profile': { displayName: 'Ama S' }, '/artists/': { id: 'acc-77', verified: true } })
    const { result } = renderHook(() => useCreatorIdentity(), { wrapper: wrapperWith() })

    await waitFor(() => expect(result.current.verified).toBe(true))
  })

  it('reports NOT verified when the artist profile 404s — never a fabricated badge', async () => {
    mockAuth(ACCOUNT)
    mockFetch({ '/studio/profile': { displayName: 'Ama S' }, '/artists/': new Error('404') })
    const { result } = renderHook(() => useCreatorIdentity(), { wrapper: wrapperWith() })

    await waitFor(() => expect(result.current.name).toBe('Ama S'))
    expect(result.current.verified).toBe(false)
  })

  it('reports NOT verified while the artist profile is still loading', () => {
    mockAuth(ACCOUNT)
    mockFetch({ '/studio/profile': { displayName: 'Ama S' }, '/artists/': { id: 'acc-77', verified: true } })
    const { result } = renderHook(() => useCreatorIdentity(), { wrapper: wrapperWith() })

    expect(result.current.verified).toBe(false)
  })

  it('yields a null id and no artist request when signed out', () => {
    mockAuth(null)
    mockFetch({ '/studio/profile': { displayName: '' } })
    const { result } = renderHook(() => useCreatorIdentity(), { wrapper: wrapperWith() })

    expect(result.current.id).toBeNull()
    expect(result.current.verified).toBe(false)
    expect(vi.mocked(client.apiFetch).mock.calls.some(([p]) => String(p).startsWith('/artists/'))).toBe(false)
  })
})
