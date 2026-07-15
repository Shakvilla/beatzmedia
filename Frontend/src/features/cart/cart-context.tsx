/**
 * Cart store — dual mode. Logged out, it's the original localStorage-backed reducer (unchanged
 * behavior, no server calls). Logged in, it's backed by TanStack Query against /v1/me/cart, with
 * mutations (add/updateQty/remove) calling the real endpoints. On the logged-out→logged-in
 * transition, any local guest-cart lines are merged into the server cart and localStorage is
 * cleared. `checkout()` always calls the real POST /v1/checkout (there is no local-only checkout
 * path — a guest attempting it gets a real 401, surfaced by the caller).
 */

import { createContext, useContext, useEffect, useMemo, useReducer, useRef, type ReactNode } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/auth-context'
import { useToast } from '../../components/ui/toast-provider'
import { ApiError } from '../../lib/api/errors'
import {
  CART_KEY,
  EMPTY_CART,
  cartQuery,
  apiAddCartItem,
  apiUpdateCartItemQty,
  apiRemoveCartItem,
  apiCheckout,
  type CartData,
  type CartItem,
  type CheckoutResultData,
} from '../../lib/api/queries/commerce'

export type { CartItem, CartItemKind } from '../../lib/api/queries/commerce'

const PERSIST_KEY = 'beatzclik-cart'

/** Flat service fee (cedis) applied to a non-empty guest cart — mirrors the server's default fee. */
const GUEST_SERVICE_FEE = 0.5

interface GuestCartState {
  items: CartItem[]
}

type GuestAction =
  | { type: 'ADD'; item: CartItem }
  | { type: 'REMOVE'; id: string }
  | { type: 'SET_QTY'; id: string; quantity: number }
  | { type: 'CLEAR' }

function guestReducer(state: GuestCartState, action: GuestAction): GuestCartState {
  switch (action.type) {
    case 'ADD': {
      const existing = state.items.find((i) => i.id === action.item.id)
      if (existing) {
        if (!existing.stackable) return state
        return {
          items: state.items.map((i) =>
            i.id === action.item.id ? { ...i, quantity: i.quantity + action.item.quantity } : i,
          ),
        }
      }
      return { items: [...state.items, action.item] }
    }
    case 'REMOVE':
      return { items: state.items.filter((i) => i.id !== action.id) }
    case 'SET_QTY':
      return {
        items: state.items.map((i) =>
          i.id === action.id ? { ...i, quantity: Math.max(1, Math.min(99, action.quantity)) } : i,
        ),
      }
    case 'CLEAR':
      return { items: [] }
    default:
      return state
  }
}

function loadGuestCart(): GuestCartState {
  try {
    const raw = typeof window !== 'undefined' ? localStorage.getItem(PERSIST_KEY) : null
    if (!raw) return { items: [] }
    const parsed = JSON.parse(raw) as Partial<GuestCartState>
    return { items: parsed.items ?? [] }
  } catch {
    return { items: [] }
  }
}

interface CartContextValue {
  items: CartItem[]
  count: number
  subtotal: number
  fee: number
  total: number
  addItem: (item: Omit<CartItem, 'quantity'> & { quantity?: number }) => void
  removeItem: (id: string) => void
  setQuantity: (id: string, quantity: number) => void
  clear: () => void
  /** Always calls the real POST /v1/checkout — there is no local-only checkout path. */
  checkout: (paymentMethodId: string, idempotencyKey: string) => Promise<CheckoutResultData>
}

const CartContext = createContext<CartContextValue | null>(null)

export function CartProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth()
  const { toast } = useToast()
  const queryClient = useQueryClient()

  // ---- guest (local) state — always maintained so a login-time merge has something to read ----
  const [guestState, dispatchGuest] = useReducer(guestReducer, undefined, loadGuestCart)
  const guestFirstRender = useRef(true)
  useEffect(() => {
    if (guestFirstRender.current) { guestFirstRender.current = false; return }
    try { localStorage.setItem(PERSIST_KEY, JSON.stringify(guestState)) } catch { /* ignore */ }
  }, [guestState])

  // ---- server (authed) state ----
  const { data: serverCart } = useQuery({ ...cartQuery(), enabled: isAuthenticated })

  // ---- merge-on-login: POST each guest line into the server cart, then clear local storage ----
  const wasAuthed = useRef(isAuthenticated)
  useEffect(() => {
    const justLoggedIn = !wasAuthed.current && isAuthenticated
    wasAuthed.current = isAuthenticated
    if (!justLoggedIn || guestState.items.length === 0) return

    const itemsToMerge = guestState.items
    void (async () => {
      for (const item of itemsToMerge) {
        try {
          await apiAddCartItem(item)
        } catch (e) {
          if (!(e instanceof ApiError && e.status === 409)) {
            toast(`Could not move "${item.title}" to your cart`, 'error')
          }
        }
      }
      dispatchGuest({ type: 'CLEAR' })
      try { localStorage.removeItem(PERSIST_KEY) } catch { /* ignore */ }
      queryClient.invalidateQueries({ queryKey: CART_KEY })
    })()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated])

  const guestSubtotal = useMemo(
    () => guestState.items.reduce((sum, i) => sum + i.price.amount * i.quantity, 0),
    [guestState.items],
  )
  const guestFee = guestState.items.length > 0 ? GUEST_SERVICE_FEE : 0
  const guestCount = useMemo(
    () => guestState.items.reduce((sum, i) => sum + i.quantity, 0),
    [guestState.items],
  )
  const guestData: CartData = {
    items: guestState.items,
    subtotal: guestSubtotal,
    fee: guestFee,
    total: guestSubtotal + guestFee,
    count: guestCount,
  }

  const data: CartData = isAuthenticated ? (serverCart ?? EMPTY_CART) : guestData

  // ---- server mutations ----
  const addMutation = useMutation({
    mutationFn: apiAddCartItem,
    onSuccess: (next) => queryClient.setQueryData<CartData>(CART_KEY, next),
    onError: () => toast('Could not add item to cart', 'error'),
  })

  const updateQtyMutation = useMutation({
    mutationFn: ({ lineId, qty }: { lineId: string; qty: number }) => apiUpdateCartItemQty(lineId, qty),
    onMutate: async ({ lineId, qty }) => {
      const prev = queryClient.getQueryData<CartData>(CART_KEY)
      if (prev) {
        queryClient.setQueryData<CartData>(CART_KEY, {
          ...prev,
          items: prev.items.map((i) => (i.id === lineId ? { ...i, quantity: qty } : i)),
        })
      }
      return { prev }
    },
    onError: (_err, _vars, ctx) => {
      if (ctx?.prev) queryClient.setQueryData(CART_KEY, ctx.prev)
      toast('Could not update quantity', 'error')
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: CART_KEY }),
  })

  const removeMutation = useMutation({
    mutationFn: apiRemoveCartItem,
    onSuccess: (next) => queryClient.setQueryData<CartData>(CART_KEY, next),
    onError: () => toast('Could not remove item from cart', 'error'),
  })

  const checkoutMutation = useMutation({
    mutationFn: ({ paymentMethodId, idempotencyKey }: { paymentMethodId: string; idempotencyKey: string }) =>
      apiCheckout(paymentMethodId, idempotencyKey),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: CART_KEY }),
  })

  const value = useMemo<CartContextValue>(
    () => ({
      items: data.items,
      count: data.count,
      subtotal: data.subtotal,
      fee: data.fee,
      total: data.total,
      addItem: (item) => {
        const full: CartItem = { quantity: 1, ...item }
        if (isAuthenticated) {
          addMutation.mutate(full)
        } else {
          dispatchGuest({ type: 'ADD', item: full })
        }
      },
      removeItem: (id) => {
        if (isAuthenticated) {
          removeMutation.mutate(id)
        } else {
          dispatchGuest({ type: 'REMOVE', id })
        }
      },
      setQuantity: (id, quantity) => {
        if (isAuthenticated) {
          updateQtyMutation.mutate({ lineId: id, qty: quantity })
        } else {
          dispatchGuest({ type: 'SET_QTY', id, quantity })
        }
      },
      clear: () => dispatchGuest({ type: 'CLEAR' }),
      checkout: (paymentMethodId, idempotencyKey) =>
        checkoutMutation.mutateAsync({ paymentMethodId, idempotencyKey }),
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [data, isAuthenticated],
  )

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useCart(): CartContextValue {
  const ctx = useContext(CartContext)
  if (!ctx) throw new Error('useCart must be used within a <CartProvider>')
  return ctx
}
