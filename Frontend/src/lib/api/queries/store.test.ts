import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as client from '../client'
import { storeListQuery, storeItemQuery } from './store'

vi.mock('../client')

const ctx = {} as never

const wireItem = {
  id: 'merch-bsherif-tee',
  type: 'MERCH',
  title: 'Iron Boy Tour Tee',
  artistName: 'Black Sherif',
  artistId: 'black-sherif',
  image: 'x',
  price: { amount: 120, currency: 'GHS' },
  genre: null,
  badges: null,
  description: null,
  popularity: null,
  createdAt: null,
  licenseOptions: null,
  variants: null,
  quality: null,
  dropsAt: null,
  stockRemaining: 42,
}

beforeEach(() => vi.resetAllMocks())

describe('storeListQuery', () => {
  it('requests the merch tab with the sort param and unwraps the page', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue({ items: [wireItem], page: 1, size: 20, total: 1 })

    const items = await storeListQuery({ type: 'MERCH', sort: 'newest' }).queryFn!(ctx)

    expect(client.apiFetch).toHaveBeenCalledWith('/store?type=MERCH&sort=newest&page=1&size=48')
    expect(items).toHaveLength(1)
    expect(items[0].price).toEqual({ amount: 120, currency: 'GHS' })
    expect(items[0].id).toBe('merch-bsherif-tee')
  })

  it('omits the type param when none is given (overview / Hi-Fi tabs)', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue({ items: [], page: 1, size: 48, total: 0 })

    await storeListQuery({ sort: 'popular' }).queryFn!(ctx)

    expect(client.apiFetch).toHaveBeenCalledWith('/store?sort=popular&page=1&size=48')
  })
})

describe('storeItemQuery', () => {
  it('fetches a single item by id', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue(wireItem)

    const item = await storeItemQuery('merch-bsherif-tee').queryFn!(ctx)

    expect(client.apiFetch).toHaveBeenCalledWith('/store/merch-bsherif-tee')
    expect(item.id).toBe('merch-bsherif-tee')
    expect(item.price).toEqual({ amount: 120, currency: 'GHS' })
  })
})
