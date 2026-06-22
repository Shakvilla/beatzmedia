/**
 * Studio payouts mock data.
 *
 * Balance, period totals, earnings history, payout methods and the ledger of
 * recent transactions. Net is the creator's take-home after Beatzclik's cut
 * (70% on sales/royalties, 90% on tips). Swap `getPayouts` for a TanStack
 * Query hook once the API exists.
 */

export type PayoutType = 'Sale' | 'Royalty' | 'Tip' | 'Cash-out'
export type PayoutStatus = 'cleared' | 'paid' | 'pending'
export type MethodKind = 'momo' | 'bank'

export interface PayoutTxn {
  id: string
  date: string
  source: string
  type: PayoutType
  /** Gross amount in cedis, or null for cash-outs. */
  gross: number | null
  /** Net change to the artist's balance (negative for cash-outs). */
  net: number
  status: PayoutStatus
}

export interface PayoutMethod {
  id: string
  label: string
  detail: string
  kind: MethodKind
  isDefault: boolean
}

export interface Payouts {
  available: number
  pending: number
  thisMonth: number
  thisMonthDelta: number
  lifetime: number
  since: string
  /** Monthly earnings history for the chart. */
  earnings: { label: string; value: number }[]
  /** This month's revenue split. */
  bySource: { sales: number; royalties: number; tips: number }
  methods: PayoutMethod[]
  transactions: PayoutTxn[]
}

export const MIN_PAYOUT = 10

export function withdrawalFee(kind: MethodKind, amount: number): number {
  if (kind === 'bank') return 5
  return Math.max(1, Math.round(amount * 0.01 * 100) / 100) // 1%, min ₵1
}

export function arrivalTime(kind: MethodKind): string {
  return kind === 'bank' ? '1–2 business days' : 'within minutes'
}

export function getPayouts(): Payouts {
  return {
    available: 18_420.5,
    pending: 1_240.8,
    thisMonth: 21_680,
    thisMonthDelta: 24,
    lifetime: 142_490,
    since: 'Jan 2024',
    earnings: [
      { label: 'Oct', value: 9_200 },
      { label: 'Nov', value: 11_400 },
      { label: 'Dec', value: 14_800 },
      { label: 'Jan', value: 12_600 },
      { label: 'Feb', value: 16_900 },
      { label: 'Mar', value: 18_300 },
      { label: 'Apr', value: 17_450 },
      { label: 'May', value: 21_680 },
    ],
    bySource: { sales: 12_400, royalties: 6_420, tips: 2_860 },
    methods: [
      { id: 'm1', label: 'MTN MoMo', detail: '0244 ··· 9210', kind: 'momo', isDefault: true },
      { id: 'm2', label: 'GCB Bank', detail: 'GCB ··· 4432', kind: 'bank', isDefault: false },
    ],
    transactions: [
      { id: 't0', date: 'May 03', source: 'Track sale · “45” × 30', type: 'Sale', gross: 75, net: 52.5, status: 'pending' },
      { id: 't1', date: 'May 02', source: 'Track sale · “Soja” × 140', type: 'Sale', gross: 350, net: 245, status: 'cleared' },
      { id: 't2', date: 'May 02', source: 'Stream royalty · 28 days', type: 'Royalty', gross: 620.4, net: 434.28, status: 'cleared' },
      { id: 't3', date: 'May 01', source: 'Tip from @ama_b', type: 'Tip', gross: 20, net: 18, status: 'cleared' },
      { id: 't4', date: 'Apr 28', source: 'Withdrawal · MTN MoMo', type: 'Cash-out', gross: null, net: -5000, status: 'paid' },
      { id: 't5', date: 'Apr 27', source: 'Track sale · “Kwaku” × 64', type: 'Sale', gross: 192, net: 134.4, status: 'cleared' },
      { id: 't6', date: 'Apr 22', source: 'Tip from @kwesi_', type: 'Tip', gross: 5, net: 4.5, status: 'cleared' },
      { id: 't7', date: 'Apr 21', source: 'Withdrawal · MTN MoMo', type: 'Cash-out', gross: null, net: -3200, status: 'paid' },
    ],
  }
}

/** Days until the next Friday (payout day). 0 = today is Friday. */
export function daysUntilFriday(from = new Date()): number {
  return (5 - from.getDay() + 7) % 7
}
