import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'

export interface ProviderSelectionView {
  capability: string
  provider: string
  version: number
  ready: boolean
  readinessReason: string
  updatedAt: string
  updatedBy: string
}

export interface ProviderConfigView {
  items: ProviderSelectionView[]
}

export interface ResendConfigView {
  configured: boolean
  from: string
  source: 'database' | 'environment' | 'not-configured' | 'unavailable'
  version: number
  updatedAt: string | null
  updatedBy: string | null
  encryptionReady: boolean
}

export interface FrogConfigView {
  configured: boolean
  senderId: string
  source: 'database' | 'environment' | 'not-configured' | 'unavailable'
  version: number
  updatedAt: string | null
  updatedBy: string | null
  encryptionReady: boolean
}

export function providerConfigQuery() {
  return queryOptions({
    queryKey: ['admin', 'provider-config'],
    queryFn: () => apiFetch<ProviderConfigView>('/admin/provider-config'),
  })
}

export function resendConfigQuery() {
  return queryOptions({
    queryKey: ['admin', 'provider-config', 'resend'],
    queryFn: () => apiFetch<ResendConfigView>('/admin/provider-config/resend'),
  })
}

export function frogConfigQuery() {
  return queryOptions({
    queryKey: ['admin', 'provider-config', 'frog'],
    queryFn: () => apiFetch<FrogConfigView>('/admin/provider-config/frog'),
  })
}

export function apiSaveResendConfig(input: {
  apiKey?: string
  from: string
  expectedVersion: number
}): Promise<ResendConfigView> {
  return apiFetch<ResendConfigView>('/admin/provider-config/resend', {
    method: 'PUT',
    body: input,
  })
}

export function apiSaveFrogConfig(input: {
  apiKey?: string
  username?: string
  senderId: string
  expectedVersion: number
}): Promise<FrogConfigView> {
  return apiFetch<FrogConfigView>('/admin/provider-config/frog', {
    method: 'PUT',
    body: input,
  })
}

export function apiSelectProvider(input: {
  capability: string
  provider: string
  expectedVersion: number
}): Promise<ProviderSelectionView> {
  return apiFetch<ProviderSelectionView>(`/admin/provider-config/${input.capability}`, {
    method: 'PUT',
    body: { provider: input.provider, expectedVersion: input.expectedVersion },
  })
}
