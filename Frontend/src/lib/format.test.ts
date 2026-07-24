import { describe, it, expect } from 'vitest'
import { relativeTime, relativeTimeAgo } from './format'

const NOW = Date.parse('2026-07-22T12:00:00Z')

describe('relativeTime', () => {
  it('just now under a minute', () => {
    expect(relativeTime('2026-07-22T11:59:30Z', NOW)).toBe('just now')
  })
  it('minutes', () => { expect(relativeTime('2026-07-22T11:30:00Z', NOW)).toBe('30m') })
  it('hours', () => { expect(relativeTime('2026-07-22T10:00:00Z', NOW)).toBe('2h') })
  it('days', () => { expect(relativeTime('2026-07-19T12:00:00Z', NOW)).toBe('3d') })
  it('returns empty string for an unparseable date', () => { expect(relativeTime('not-a-date', NOW)).toBe('') })
})

describe('relativeTimeAgo', () => {
  it('appends ago for non-zero', () => { expect(relativeTimeAgo('2026-07-22T10:00:00Z', NOW)).toBe('2h ago') })
  it('keeps just now as-is', () => { expect(relativeTimeAgo('2026-07-22T11:59:40Z', NOW)).toBe('just now') })
  it('returns empty string (not " ago") for an unparseable date', () => { expect(relativeTimeAgo('not-a-date', NOW)).toBe('') })
})
