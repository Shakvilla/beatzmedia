import { describe, expect, it } from 'vitest'
import { relativeTime, relativeTimeAgo, monthYear } from './format'

describe('relativeTime', () => {
  const now = Date.parse('2025-01-01T12:00:00Z')
  it('under a minute → just now', () => expect(relativeTime('2025-01-01T11:59:30Z', now)).toBe('just now'))
  it('minutes', () => expect(relativeTime('2025-01-01T11:30:00Z', now)).toBe('30m'))
  it('hours', () => expect(relativeTime('2025-01-01T09:00:00Z', now)).toBe('3h'))
  it('days', () => expect(relativeTime('2024-12-30T12:00:00Z', now)).toBe('2d'))
  it('unparseable → empty string', () => expect(relativeTime('not-a-date', now)).toBe(''))
  it('future clamps to just now', () => expect(relativeTime('2025-01-01T13:00:00Z', now)).toBe('just now'))
})

describe('relativeTimeAgo', () => {
  const now = Date.parse('2025-01-01T12:00:00Z')
  it('appends ago for buckets', () => expect(relativeTimeAgo('2025-01-01T09:00:00Z', now)).toBe('3h ago'))
  it('leaves just now unchanged', () => expect(relativeTimeAgo('2025-01-01T11:59:59Z', now)).toBe('just now'))
  it('empty stays empty (no bare " ago")', () => expect(relativeTimeAgo('nope', now)).toBe(''))
})

describe('monthYear', () => {
  it('formats mid-month ISO as Mon YYYY', () => expect(monthYear('2024-03-15T10:30:00Z')).toBe('Mar 2024'))
  it('unparseable → empty string', () => expect(monthYear('nope')).toBe(''))
})
