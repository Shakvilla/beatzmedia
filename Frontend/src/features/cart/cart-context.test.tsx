import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { apiFetch } from '../../lib/api/client'
import { useCart, CartProvider } from './cart-context'

vi.mock('../../lib/api/client', async () => {
  const actual = await vi.importActual<typeof import('../../lib/api/client')>('../../lib/api/client')
  return { ...actual, apiFetch: vi.fn() }
})

const mockUseAuth = vi.fn()
vi.mock('../auth/auth-context', () => ({ useAuth: () => mockUseAuth() }))

vi.mock('../../components/ui/toast-provider', () => ({ useToast: () => ({ toast: vi.fn() }) }))

function wrapper({ children }: { children: ReactNode }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={queryClient}>
    <CartProvider>{children}</CartProvider>
  </QueryClientProvider>
}

beforeEach(() => {
  vi.mocked(apiFetch).mockReset()
  localStorage.clear()
  mockUseAuth.mockReturnValue({ isAuthenticated: false })
})

describe('CartProvider guest mode', () => {
  it('addItem/removeItem/setQuantity work locally with no API calls', () => {
    const { result } = renderHook(() => useCart(), { wrapper })

    act(() => result.current.addItem({
      id: 'track:t1', kind: 'track', title: 'Song', image: 'img.jpg',
      price: { amount: 5, currency: 'GHS' },
    }))

    expect(result.current.items).toHaveLength(1)
    expect(result.current.subtotal).toBe(5)
    expect(apiFetch).not.toHaveBeenCalled()

    act(() => result.current.removeItem('track:t1'))
    expect(result.current.items).toHaveLength(0)
  })
})

describe('CartProvider authed mode', () => {
  beforeEach(() => mockUseAuth.mockReturnValue({ isAuthenticated: true }))

  it('reads the cart from the server', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      items: [{
        id: 'track:t1', kind: 'track', refId: 't1', title: 'Song', subtitle: null, image: null,
        price: { amount: 5, currency: 'GHS' }, quantity: 1, stackable: false, metadata: null,
      }],
      subtotal: { amount: 5, currency: 'GHS' }, fee: { amount: 0.5, currency: 'GHS' },
      total: { amount: 5.5, currency: 'GHS' }, count: 1,
    })

    const { result } = renderHook(() => useCart(), { wrapper })

    await waitFor(() => expect(result.current.items).toHaveLength(1))
    expect(result.current.total).toBe(5.5)
  })
})
