import { describe, it, expect } from 'vitest'
import { unwrapPage, type PageWire } from './pagination'

describe('unwrapPage', () => {
  it('maps each item and drops the envelope', () => {
    const page: PageWire<{ n: number }> = { items: [{ n: 1 }, { n: 2 }], page: 1, size: 20, total: 2 }
    expect(unwrapPage(page, (x) => x.n * 10)).toEqual([10, 20])
  })
  it('returns [] for an empty page', () => {
    expect(unwrapPage({ items: [], page: 1, size: 20, total: 0 }, (x) => x)).toEqual([])
  })
})
