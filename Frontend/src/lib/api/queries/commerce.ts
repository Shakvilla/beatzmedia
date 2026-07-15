import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import type { Money } from '../../../types'

export const CART_KEY = ['cart'] as const

export type CartItemKind =
  | 'track'
  | 'album'
  | 'album-rest'
  | 'store'
  | 'episode'
  | 'season-pass'
  | 'ticket'

export interface CartItem {
  /** Stable line id, always `${kind}:${refId}` (refId may itself contain colons). */
  id: string
  kind: CartItemKind
  title: string
  subtitle?: string
  image: string
  price: Money
  quantity: number
  stackable?: boolean
}

export interface CartData {
  items: CartItem[]
  subtotal: number
  fee: number
  total: number
  count: number
}

export const EMPTY_CART: CartData = { items: [], subtotal: 0, fee: 0, total: 0, count: 0 }

interface CartItemWire {
  id: string
  kind: string
  refId: string
  title: string
  subtitle: string | null
  image: string | null
  price: Money
  quantity: number
  stackable: boolean
  metadata: Record<string, unknown> | null
}

interface CartViewWire {
  items: CartItemWire[]
  subtotal: Money
  fee: Money
  total: Money
  count: number
}

function toCartItem(w: CartItemWire): CartItem {
  return {
    id: w.id,
    kind: w.kind as CartItemKind,
    title: w.title,
    subtitle: w.subtitle ?? undefined,
    image: w.image ?? '',
    price: w.price,
    quantity: w.quantity,
    stackable: w.stackable,
  }
}

export function toCartData(w: CartViewWire): CartData {
  return {
    items: w.items.map(toCartItem),
    subtotal: w.subtotal.amount,
    fee: w.fee.amount,
    total: w.total.amount,
    count: w.count,
  }
}

export function cartQuery() {
  return queryOptions({
    queryKey: CART_KEY,
    queryFn: async () => toCartData(await apiFetch<CartViewWire>('/me/cart', undefined)),
  })
}

// ---- cart line id <-> {kind, refId} ----
// Mirrors the backend exactly: CartItem.lineIdFor(kind, refId) = kind.wireValue() + ":" + refId.
// refId may itself contain colons (e.g. ticket "evt-1:VIP"), so only the FIRST colon is a separator.

export function parseLineId(id: string): { kind: CartItemKind; refId: string } {
  const sep = id.indexOf(':')
  if (sep === -1) {
    throw new Error(`Invalid cart line id (expected "kind:refId"): ${id}`)
  }
  return { kind: id.slice(0, sep) as CartItemKind, refId: id.slice(sep + 1) }
}

// ---- raw API calls ----

export async function apiAddCartItem(
  item: Omit<CartItem, 'quantity'> & { quantity?: number },
): Promise<CartData> {
  const { kind, refId } = parseLineId(item.id)
  return toCartData(
    await apiFetch<CartViewWire>('/me/cart/items', {
      method: 'POST',
      body: {
        kind,
        refId,
        qty: item.quantity,
        metadata: {
          title: item.title,
          subtitle: item.subtitle,
          image: item.image,
          priceMinor: Math.round(item.price.amount * 100),
        },
      },
    }),
  )
}

export async function apiUpdateCartItemQty(lineId: string, qty: number): Promise<CartData> {
  return toCartData(
    await apiFetch<CartViewWire>(`/me/cart/items/${lineId}`, { method: 'PATCH', body: { qty } }),
  )
}

export async function apiRemoveCartItem(lineId: string): Promise<CartData> {
  return toCartData(await apiFetch<CartViewWire>(`/me/cart/items/${lineId}`, { method: 'DELETE' }))
}

// ---- checkout ----

export interface CheckoutResultData {
  orderId: string
  reference: string
  paymentIntentId: string
  status: string
}

export async function apiCheckout(
  paymentMethodId: string,
  idempotencyKey: string,
): Promise<CheckoutResultData> {
  return apiFetch<CheckoutResultData>('/checkout', {
    method: 'POST',
    body: { paymentMethodId },
    idempotencyKey,
  })
}

// ---- order detail (checkout settlement poll target) ----

export interface OrderLine {
  id: string
  kind: CartItemKind
  refId: string
  title: string
  subtitle?: string
  image?: string
  unitPrice: Money
  quantity: number
}

export interface OrderData {
  orderId: string
  reference: string
  status: string
  items: OrderLine[]
  subtotal: number
  fee: number
  total: number
  createdAt: string | null
}

interface OrderLineWire {
  id: string
  kind: string
  refId: string
  title: string
  subtitle: string | null
  image: string | null
  unitPrice: Money
  quantity: number
}

interface OrderSnapshotWire {
  items: OrderLineWire[]
  subtotal: Money
  fee: Money
  total: Money
  reference: string
  orderId: string
  status: string
  createdAt: string | null
}

function toOrderData(w: OrderSnapshotWire): OrderData {
  return {
    orderId: w.orderId,
    reference: w.reference,
    status: w.status,
    items: w.items.map((l) => ({
      id: l.id,
      kind: l.kind as CartItemKind,
      refId: l.refId,
      title: l.title,
      subtitle: l.subtitle ?? undefined,
      image: l.image ?? undefined,
      unitPrice: l.unitPrice,
      quantity: l.quantity,
    })),
    subtotal: w.subtotal.amount,
    fee: w.fee.amount,
    total: w.total.amount,
    createdAt: w.createdAt,
  }
}

export function orderQuery(orderId: string) {
  return queryOptions({
    queryKey: ['order', orderId],
    queryFn: async () =>
      toOrderData(await apiFetch<OrderSnapshotWire>(`/me/orders/${orderId}`, undefined)),
  })
}
