import { describe, expect, it } from 'vitest'
import { relativeTime, relativeTimeAgo, dayMonthYear, monthDay, toCedis, dueLabel } from './format'

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

describe('dayMonthYear', () => {
  it('formats mid-month ISO as D Mon YYYY', () => expect(dayMonthYear('2024-03-15T10:30:00Z')).toBe('15 Mar 2024'))
  // GAP-16: the old formatter dropped the day, so an admin could not tell a 1 August signup from a
  // 31 August one. Pinned to UTC so the label does not shift with the viewer's timezone.
  it('keeps the day near a timezone boundary', () => expect(dayMonthYear('2024-03-01T00:30:00Z')).toBe('1 Mar 2024'))
  it('unparseable → empty string', () => expect(dayMonthYear('nope')).toBe(''))
})

describe('monthDay', () => {
  it('formats an ISO timestamp as short month + day', () => {
    expect(monthDay('2026-04-22T10:00:00Z')).toBe('Apr 22')
    expect(monthDay('2026-05-02T23:59:00Z')).toBe('May 02')
  })

  it('returns an empty string for an unparseable value', () => {
    expect(monthDay('not-a-date')).toBe('')
    expect(monthDay('')).toBe('')
  })
})

describe('toCedis', () => {
  it('passes a bare number through (overview + ledger shape)', () => {
    expect(toCedis(842000)).toBe(842000)
    expect(toCedis(-42.5)).toBe(-42.5)
  })

  it('unwraps the { amount, currency } MoneyView shape (disputes + payouts)', () => {
    expect(toCedis({ amount: 18.99, currency: 'GHS' })).toBe(18.99)
  })

  it('treats null/undefined as 0 so money never renders NaN', () => {
    expect(toCedis(null)).toBe(0)
    expect(toCedis(undefined)).toBe(0)
  })
})

describe('dueLabel', () => {
  const NOW = Date.parse('2026-07-31T12:00:00Z')

  it('reads "completed" for a completed request regardless of the date', () => {
    expect(dueLabel('2026-07-01T12:00:00Z', 'completed', NOW)).toBe('completed')
  })

  it('counts days forward for a future due date', () => {
    expect(dueLabel('2026-08-12T12:00:00Z', 'new', NOW)).toBe('in 12 days')
    expect(dueLabel('2026-08-01T12:00:00Z', 'new', NOW)).toBe('in 1 day')
  })

  it('says due today when it lands inside the current day', () => {
    expect(dueLabel('2026-07-31T18:00:00Z', 'new', NOW)).toBe('due today')
  })

  it('counts days back for an overdue date, singular at one', () => {
    expect(dueLabel('2026-07-30T12:00:00Z', 'overdue', NOW)).toBe('overdue 1 day')
    expect(dueLabel('2026-07-25T12:00:00Z', 'overdue', NOW)).toBe('overdue 6 days')
  })

  it('returns an em dash when there is no due date, rather than inventing one', () => {
    expect(dueLabel(null, 'new', NOW)).toBe('—')
    expect(dueLabel('not-a-date', 'new', NOW)).toBe('—')
  })
})
