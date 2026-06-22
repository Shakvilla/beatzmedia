/**
 * Global cart store.
 *
 * Holds whatever the user is about to buy across the app — tracks, albums,
 * store items, premium podcast episodes, season passes and event tickets.
 * "Add to cart" buttons write here; the cart and checkout pages read from here.
 * Tips are NOT cart items — they're instant MoMo sends.
 *
 * Digital one-off goods (a track, album, episode, season pass) can only be
 * bought once, so they're not stackable. Tickets and merch are stackable.
 */

import { createContext, useContext, useEffect, useMemo, useReducer, useRef, type ReactNode } from 'react'
import type { Money } from '../../types'

const PERSIST_KEY = 'beatzclik-cart'

export type CartItemKind = 'track' | 'album' | 'album-rest' | 'store' | 'episode' | 'season-pass' | 'ticket'

export interface CartItem {
  /** Stable line id, e.g. "track:last-last" or "ticket:iron-boy-live:VIP". */
  id: string
  kind: CartItemKind
  title: string
  subtitle?: string
  image: string
  /** Unit price. */
  price: Money
  quantity: number
  /** Whether quantity can exceed 1 (tickets, merch). */
  stackable?: boolean
}

export interface OrderSnapshot {
  items: CartItem[]
  subtotal: number
  fee: number
  total: number
  reference: string
}

/** Flat service fee (cedis) applied to a non-empty cart. */
const SERVICE_FEE = 0.5

interface CartState {
  items: CartItem[]
  lastOrder: OrderSnapshot | null
}

type Action =
  | { type: 'ADD'; item: CartItem }
  | { type: 'REMOVE'; id: string }
  | { type: 'SET_QTY'; id: string; quantity: number }
  | { type: 'CLEAR' }
  | { type: 'CHECKOUT'; order: OrderSnapshot }

function reducer(state: CartState, action: Action): CartState {
  switch (action.type) {
    case 'ADD': {
      const existing = state.items.find((i) => i.id === action.item.id)
      if (existing) {
        if (!existing.stackable) return state // already owned-once item
        return {
          ...state,
          items: state.items.map((i) =>
            i.id === action.item.id ? { ...i, quantity: i.quantity + action.item.quantity } : i,
          ),
        }
      }
      return { ...state, items: [...state.items, action.item] }
    }
    case 'REMOVE':
      return { ...state, items: state.items.filter((i) => i.id !== action.id) }
    case 'SET_QTY':
      return {
        ...state,
        items: state.items.map((i) =>
          i.id === action.id ? { ...i, quantity: Math.max(1, Math.min(99, action.quantity)) } : i,
        ),
      }
    case 'CLEAR':
      return { ...state, items: [] }
    case 'CHECKOUT':
      return { items: [], lastOrder: action.order }
    default:
      return state
  }
}

interface CartContextValue extends CartState {
  count: number
  subtotal: number
  fee: number
  total: number
  addItem: (item: Omit<CartItem, 'quantity'> & { quantity?: number }) => void
  removeItem: (id: string) => void
  setQuantity: (id: string, quantity: number) => void
  clear: () => void
  /** Snapshots the cart as an order, clears it, and returns the reference. */
  checkout: () => string
}

const CartContext = createContext<CartContextValue | null>(null)

function genReference(): string {
  const n = Math.floor(10000 + Math.random() * 90000)
  return `BZ-${new Date().getFullYear()}-${n}`
}

function load(): CartState {
  try {
    const raw = typeof window !== 'undefined' ? localStorage.getItem(PERSIST_KEY) : null
    if (!raw) return { items: [], lastOrder: null }
    const parsed = JSON.parse(raw) as Partial<CartState>
    return { items: parsed.items ?? [], lastOrder: parsed.lastOrder ?? null }
  } catch {
    return { items: [], lastOrder: null }
  }
}

export function CartProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(reducer, undefined, load)
  const first = useRef(true)

  useEffect(() => {
    if (first.current) { first.current = false; return }
    try { localStorage.setItem(PERSIST_KEY, JSON.stringify(state)) } catch { /* ignore */ }
  }, [state])

  const subtotal = useMemo(
    () => state.items.reduce((sum, i) => sum + i.price.amount * i.quantity, 0),
    [state.items],
  )
  const fee = state.items.length > 0 ? SERVICE_FEE : 0
  const total = subtotal + fee
  const count = useMemo(() => state.items.reduce((sum, i) => sum + i.quantity, 0), [state.items])

  const value = useMemo<CartContextValue>(
    () => ({
      ...state,
      count,
      subtotal,
      fee,
      total,
      addItem: (item) => dispatch({ type: 'ADD', item: { quantity: 1, ...item } }),
      removeItem: (id) => dispatch({ type: 'REMOVE', id }),
      setQuantity: (id, quantity) => dispatch({ type: 'SET_QTY', id, quantity }),
      clear: () => dispatch({ type: 'CLEAR' }),
      checkout: () => {
        const reference = genReference()
        dispatch({
          type: 'CHECKOUT',
          order: { items: state.items, subtotal, fee, total, reference },
        })
        return reference
      },
    }),
    [state, count, subtotal, fee, total],
  )

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useCart(): CartContextValue {
  const ctx = useContext(CartContext)
  if (!ctx) throw new Error('useCart must be used within a <CartProvider>')
  return ctx
}
