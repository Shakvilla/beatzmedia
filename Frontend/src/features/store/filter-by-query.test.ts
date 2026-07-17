import { describe, it, expect } from 'vitest'
import { filterByQuery } from './filter-by-query'
import type { StoreItem } from '../../types'

describe('filterByQuery', () => {
  const items = [
    { title: 'Sunrise', artistName: 'Golden Hour' } as StoreItem,
    { title: 'Moonlight', artistName: 'Luna Waves' } as StoreItem,
    { title: 'Echoes', artistName: 'Aurora' } as StoreItem,
  ]

  it('returns all items when q is undefined', () => {
    const result = filterByQuery(items, undefined)
    expect(result).toHaveLength(3)
    expect(result).toEqual(items)
  })

  it('returns all items when q is empty string', () => {
    const result = filterByQuery(items, '')
    expect(result).toHaveLength(3)
    expect(result).toEqual(items)
  })

  it('matches case-insensitively on title', () => {
    const result = filterByQuery(items, 'sunrise')
    expect(result).toHaveLength(1)
    expect(result[0].title).toBe('Sunrise')
  })

  it('matches case-insensitively on artistName', () => {
    const result = filterByQuery(items, 'LUNA')
    expect(result).toHaveLength(1)
    expect(result[0].artistName).toBe('Luna Waves')
  })

  it('matches partial substrings in title', () => {
    const result = filterByQuery(items, 'moon')
    expect(result).toHaveLength(1)
    expect(result[0].title).toBe('Moonlight')
  })

  it('matches partial substrings in artistName', () => {
    const result = filterByQuery(items, 'hour')
    expect(result).toHaveLength(1)
    expect(result[0].artistName).toBe('Golden Hour')
  })

  it('returns empty array when query matches nothing', () => {
    const result = filterByQuery(items, 'xyz')
    expect(result).toHaveLength(0)
    expect(result).toEqual([])
  })

  it('matches across both title and artistName', () => {
    const result = filterByQuery(items, 'a')
    expect(result).toHaveLength(2) // "Aurora" artistName + "Luna Waves" artistName
  })
})
