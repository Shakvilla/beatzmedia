/** Display formatters for domain values. Keep all string-shaping logic here. */

import type { Money } from '../types'

/** 192 -> "3:12", 72 -> "1:12". */
export function formatDuration(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds))
  const minutes = Math.floor(safe / 60)
  const seconds = safe % 60
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}

/** { amount: 2.5, currency: 'GHS' } -> "₵2.50". */
export function formatPrice(price?: Money): string {
  if (!price) return ''
  const symbol = price.currency === 'GHS' ? '₵' : ''
  return `${symbol}${price.amount.toFixed(2)}`
}

/** 1_200_000_000 -> "1.2B", 8_400_000 -> "8.4M", 1_240 -> "1.2K". */
export function formatCount(value?: number): string {
  if (value == null) return '-'
  if (value < 1000) return value.toString()
  const units = [
    { limit: 1_000_000_000, suffix: 'B' },
    { limit: 1_000_000, suffix: 'M' },
    { limit: 1_000, suffix: 'K' },
  ]
  for (const { limit, suffix } of units) {
    if (value >= limit) {
      const scaled = value / limit
      // One decimal, but drop a trailing ".0".
      const text = scaled >= 100 ? Math.round(scaled).toString() : scaled.toFixed(1)
      return `${text.replace(/\.0$/, '')}${suffix}`
    }
  }
  return value.toString()
}

/** Total seconds across a list of durations -> "49 min" / "1 hr 4 min". */
export function formatTotalDuration(totalSeconds: number): string {
  const minutes = Math.round(totalSeconds / 60)
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest ? `${hours} hr ${rest} min` : `${hours} hr`
}
