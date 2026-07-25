import { describe, expect, it, vi } from 'vitest'
import { useServerPaged } from './pagination'

describe('useServerPaged', () => {
  const setPage = vi.fn()

  it('derives pageCount from the server total, not the item count', () => {
    const p = useServerPaged({ items: [1, 2, 3, 4, 5, 6, 7, 8], total: 137, page: 2, setPage, size: 8 })
    expect(p.pageCount).toBe(18)
    expect(p.total).toBe(137)
    expect(p.page).toBe(2)
  })

  it('passes the server-sliced items straight through as the page items', () => {
    const p = useServerPaged({ items: ['a', 'b'], total: 2, page: 1, setPage, size: 8 })
    expect(p.pageItems).toEqual(['a', 'b'])
  })

  it('never reports fewer than one page, even when empty', () => {
    const p = useServerPaged({ items: [], total: 0, page: 1, setPage, size: 8 })
    expect(p.pageCount).toBe(1)
    expect(p.pageItems).toEqual([])
  })
})
