import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'

export interface FanSettingsWire {
  theme: string
  audioQuality: string
  streamingQuality: string
  downloadQuality: string
  crossfade: string
  dataSaver: boolean
  notifications: {
    newReleases: boolean
    playlistUpdates: boolean
    dropsOffers: boolean
  }
  country: string
  phone: string | null
  phoneVerified: boolean
}

export interface PhoneVerificationStartView {
  maskedPhone: string
  alreadyVerified: boolean
  expiresAt: string | null
  resendAvailableAt: string | null
}

export function fanSettingsQuery() {
  return queryOptions({
    queryKey: ['me', 'settings'],
    queryFn: () => apiFetch<FanSettingsWire>('/me/settings'),
  })
}

export function apiStartPhoneVerification(phone: string): Promise<PhoneVerificationStartView> {
  return apiFetch<PhoneVerificationStartView>('/me/phone-verification', {
    method: 'POST',
    body: { phone },
  })
}

export function apiConfirmPhoneVerification(code: string): Promise<FanSettingsWire> {
  return apiFetch<FanSettingsWire>('/me/phone-verification/confirm', {
    method: 'POST',
    body: { code },
  })
}
