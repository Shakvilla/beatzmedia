import { describe, it, expect, vi, beforeEach } from 'vitest'
import { apiFetch } from '../client'
import {
  cartQuery,
  orderQuery,
  parseLineId,
  apiAddCartItem,
  apiUpdateCartItemQty,
  apiRemoveCartItem,
  apiCheckout,
  EMPTY_CART,
  type CartItem,
} from './commerce'

vi.mock('../client', () => ({ apiFetch: vi.fn() }))

describe('commerce id parsing (pure)', () => {
  it('parseLineId splits kind:refId on the FIRST colon only', () => {
    expect(parseLineId('track:t1')).toEqual({ kind: 'track', refId: 't1' })
    expect(parseLineId('ticket:some-event:VIP')).toEqual({ kind: 'ticket', refId: 'some-event:VIP' })
    expect(parseLineId('store:item-1:M')).toEqual({ kind: 'store', refId: 'item-1:M' })
  })

  it('parseLineId throws on a malformed id with no colon', () => {
    expect(() => parseLineId('no-colon-here')).toThrow()
  })
})

describe('commerce API calls', () => {
  beforeEach(() => vi.mocked(apiFetch).mockReset())

  const wireCart = {
    items: [
      {
        id: 'track:t1', kind: 'track', refId: 't1', title: 'Song', subtitle: 'Artist',
        image: 'img.jpg', price: { amount: 5, currency: 'GHS' }, quantity: 1, stackable: false,
        metadata: null,
      },
    ],
    subtotal: { amount: 5, currency: 'GHS' },
    fee: { amount: 0.5, currency: 'GHS' },
    total: { amount: 5.5, currency: 'GHS' },
    count: 1,
  }

  it('cartQuery fetches and maps the wire cart, defaulting missing subtitle/image', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      ...wireCart,
      items: [{ ...wireCart.items[0], subtitle: null, image: null }],
    })

    const result = await cartQuery().queryFn(expect.anything())

    expect(apiFetch).toHaveBeenCalledWith('/me/cart', undefined)
    expect(result.items[0].subtitle).toBeUndefined()
    expect(result.items[0].image).toBe('')
    expect(result.subtotal).toBe(5)
    expect(result.fee).toBe(0.5)
    expect(result.total).toBe(5.5)
    expect(result.count).toBe(1)
  })

  it('apiAddCartItem parses the line id and POSTs kind/refId/qty/metadata', async () => {
    vi.mocked(apiFetch).mockResolvedValue(wireCart)
    const item: Omit<CartItem, 'quantity'> & { quantity?: number } = {
      id: 'ticket:evt-1:VIP', kind: 'ticket', title: 'VIP Ticket', subtitle: 'Venue',
      image: 'img.jpg', price: { amount: 100, currency: 'GHS' }, quantity: 2,
    }

    await apiAddCartItem(item)

    expect(apiFetch).toHaveBeenCalledWith('/me/cart/items', {
      method: 'POST',
      body: {
        kind: 'ticket',
        refId: 'evt-1:VIP',
        qty: 2,
        metadata: { title: 'VIP Ticket', subtitle: 'Venue', image: 'img.jpg', priceMinor: 10000 },
      },
    })
  })

  it('apiUpdateCartItemQty PATCHes qty by lineId', async () => {
    vi.mocked(apiFetch).mockResolvedValue(wireCart)

    await apiUpdateCartItemQty('ticket:evt-1:VIP', 3)

    expect(apiFetch).toHaveBeenCalledWith('/me/cart/items/ticket:evt-1:VIP', {
      method: 'PATCH',
      body: { qty: 3 },
    })
  })

  it('apiRemoveCartItem DELETEs by lineId', async () => {
    vi.mocked(apiFetch).mockResolvedValue(wireCart)

    await apiRemoveCartItem('track:t1')

    expect(apiFetch).toHaveBeenCalledWith('/me/cart/items/track:t1', { method: 'DELETE' })
  })

  it('apiCheckout POSTs paymentMethodId with the Idempotency-Key header', async () => {
    const result = {
      orderId: 'o1', reference: 'BZ-2026-00001', paymentIntentId: 'pi1', status: 'pending',
    }
    vi.mocked(apiFetch).mockResolvedValue(result)

    const got = await apiCheckout('mtn', 'idem-key-1')

    expect(apiFetch).toHaveBeenCalledWith('/checkout', {
      method: 'POST',
      body: { paymentMethodId: 'mtn' },
      idempotencyKey: 'idem-key-1',
    })
    expect(got).toEqual(result)
  })

  it('orderQuery fetches and maps the wire order snapshot', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      orderId: 'o1', reference: 'BZ-2026-00001', status: 'paid',
      items: [{
        id: 'l1', kind: 'track', refId: 't1', title: 'Song', subtitle: 'Artist', image: 'img.jpg',
        unitPrice: { amount: 5, currency: 'GHS' }, quantity: 1,
      }],
      subtotal: { amount: 5, currency: 'GHS' }, fee: { amount: 0.5, currency: 'GHS' },
      total: { amount: 5.5, currency: 'GHS' }, createdAt: '2026-07-15T10:00:00Z',
    })

    const result = await orderQuery('o1').queryFn(expect.anything())

    expect(apiFetch).toHaveBeenCalledWith('/me/orders/o1', undefined)
    expect(result.status).toBe('paid')
    expect(result.items[0].subtitle).toBe('Artist')
    expect(result.total).toBe(5.5)
  })

  it('EMPTY_CART has zeroed totals and no items', () => {
    expect(EMPTY_CART).toEqual({ items: [], subtotal: 0, fee: 0, total: 0, count: 0 })
  })
})
