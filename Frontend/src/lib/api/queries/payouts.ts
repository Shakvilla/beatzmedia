import { queryOptions } from '@tanstack/react-query'
import type { Payouts, PayoutMethod } from '../../studio-payouts'
import { apiFetch } from '../client'
import { toPayouts, type PayoutsWire, toPayoutMethod, type PayoutMethodWire } from '../mappers'

/** `GET /v1/studio/payouts` — the signed-in creator's balance + stats + earnings + methods + ledger. */
export function payoutsQuery() {
  return queryOptions<Payouts>({
    queryKey: ['studio', 'payouts'],
    queryFn: async () => toPayouts(await apiFetch<PayoutsWire>('/studio/payouts')),
  })
}

/**
 * `POST /v1/studio/payouts/withdraw` — request a cash-out. Money POST: server computes the fee and
 * gates KYC/floor/balance. A fresh `Idempotency-Key` makes a double-click one withdrawal.
 */
export function apiRequestWithdrawal(amountCedis: number, methodId: string): Promise<void> {
  return apiFetch<void>('/studio/payouts/withdraw', {
    method: 'POST',
    body: { amount: { amount: amountCedis, currency: 'GHS' }, methodId },
    idempotencyKey: crypto.randomUUID(),
  })
}

export interface NewPayoutMethodInput {
  kind: 'momo' | 'bank'
  label: string
  detail: string
  network: string | null
  walletNumber: string | null
  bankName: string | null
  bankCode: string | null
  accountName: string | null
  accountNumber: string | null
}

/** `POST /v1/studio/payout-methods` — add a structured cash-out destination (WU-PAY-7). */
export function apiAddPayoutMethod(input: NewPayoutMethodInput): Promise<PayoutMethod> {
  return apiFetch<PayoutMethodWire>('/studio/payout-methods', { method: 'POST', body: input }).then(toPayoutMethod)
}

/** `PATCH /v1/studio/payout-methods/:id/default`. */
export function apiSetDefaultPayoutMethod(id: string): Promise<PayoutMethod> {
  return apiFetch<PayoutMethodWire>(`/studio/payout-methods/${id}/default`, { method: 'PATCH' }).then(toPayoutMethod)
}

/** `DELETE /v1/studio/payout-methods/:id`. */
export function apiRemovePayoutMethod(id: string): Promise<void> {
  return apiFetch<void>(`/studio/payout-methods/${id}`, { method: 'DELETE' })
}

/** MoMo networks the backend accepts (Provider wire tokens). */
export const MOMO_NETWORKS: { value: string; label: string }[] = [
  { value: 'mtn', label: 'MTN' },
  { value: 'telecel', label: 'Telecel' },
  { value: 'airteltigo', label: 'AirtelTigo' },
]

/** Ghana bank codes the backend accepts (GhanaBankCode tokens), mirrored — no endpoint lists them. */
export const GHANA_BANK_CODES: { code: string; name: string }[] = [
  { code: 'GCB', name: 'GCB Bank' }, { code: 'CAL', name: 'CalBank' }, { code: 'EBL', name: 'Ecobank Ghana' },
  { code: 'ABB', name: 'Absa Bank Ghana' }, { code: 'UBA', name: 'United Bank for Africa' },
  { code: 'ADB', name: 'Agricultural Development Bank' }, { code: 'APX', name: 'Apex Bank' },
  { code: 'BOA', name: 'Bank of Africa' }, { code: 'BBG', name: 'Bank of Baroda Ghana' },
  { code: 'FBN', name: 'First Bank of Nigeria' }, { code: 'FBL', name: 'Fidelity Bank Ghana' },
  { code: 'FAB', name: 'First Atlantic Bank' }, { code: 'FNB', name: 'First National Bank Ghana' },
  { code: 'GTB', name: 'Guaranty Trust Bank Ghana' }, { code: 'NIB', name: 'National Investment Bank' },
  { code: 'OMN', name: 'OmniBSIC Bank' }, { code: 'PBL', name: 'Prudential Bank' },
  { code: 'RBL', name: 'Republic Bank Ghana' }, { code: 'SGG', name: 'Societe Generale Ghana' },
  { code: 'CBG', name: 'Consolidated Bank Ghana' }, { code: 'SBL', name: 'Stanbic Bank Ghana' },
  { code: 'SCB', name: 'Standard Chartered Bank Ghana' }, { code: 'ZBL', name: 'Zenith Bank Ghana' },
]
