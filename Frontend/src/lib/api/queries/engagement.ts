import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'

export type ListeningContentKind = 'track' | 'podcast-episode'
export type ListeningEventType =
  | 'start'
  | 'qualified-play'
  | 'progress'
  | 'completion'
  | 'skip'
  | 'repeat'

export interface ListeningSessionStart {
  recording: boolean
  sessionId: string | null
  startedAt: string | null
}

export interface ListeningCheckpointInput {
  contentKind: ListeningContentKind
  contentId: string
  eventType: ListeningEventType
  positionSeconds: number
  durationSeconds: number
  listenedDeltaSeconds: number
  recommendationId?: string
}

export interface ListeningHistoryItem {
  contentKind: ListeningContentKind
  contentId: string
  title: string
  subtitle: string
  image: string
  positionSeconds: number
  durationSeconds: number
  completed: boolean
  lastActivityAt: string
}

export interface ListeningHistoryPage {
  items: ListeningHistoryItem[]
  nextCursor: string | null
}

export interface ListeningSettings {
  historyEnabled: boolean
}

export function apiStartListeningSession(): Promise<ListeningSessionStart> {
  return apiFetch<ListeningSessionStart>('/me/listening-sessions', { method: 'POST' })
}

export function apiPutListeningCheckpoint(
  sessionId: string,
  sequence: number,
  input: ListeningCheckpointInput,
): Promise<void> {
  return apiFetch<void>(
    `/me/listening-sessions/${encodeURIComponent(sessionId)}/checkpoints/${sequence}`,
    { method: 'PUT', body: input },
  )
}

export function recentListeningQuery(enabled: boolean, limit = 12) {
  return queryOptions({
    queryKey: ['me', 'listening', 'recent', limit],
    queryFn: () => apiFetch<ListeningHistoryPage>(`/me/listening/recent?limit=${limit}`),
    enabled,
  })
}

export function continueListeningQuery(enabled: boolean, limit = 12) {
  return queryOptions({
    queryKey: ['me', 'listening', 'continue', limit],
    queryFn: () => apiFetch<ListeningHistoryPage>(`/me/listening/continue?limit=${limit}`),
    enabled,
  })
}

export function listeningSettingsQuery(enabled: boolean) {
  return queryOptions({
    queryKey: ['me', 'listening', 'settings'],
    queryFn: () => apiFetch<ListeningSettings>('/me/listening/settings'),
    enabled,
  })
}

export function apiUpdateListeningSettings(historyEnabled: boolean): Promise<ListeningSettings> {
  return apiFetch<ListeningSettings>('/me/listening/settings', {
    method: 'PUT',
    body: { historyEnabled },
  })
}

export function apiClearListeningHistory(): Promise<void> {
  return apiFetch<void>('/me/listening/history', { method: 'DELETE' })
}
