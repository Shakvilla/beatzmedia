import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'

export type RecommendationKind = 'single' | 'album' | 'ep' | 'podcast'

export interface RecommendationItem {
  kind: RecommendationKind
  contentId: string
  creatorId: string
  title: string
  subtitle: string
  image: string | null
  route: string
  reason: string
  recommendationId: string | null
  rankingVersion: string
  position: number
}

export interface RecommendationPage {
  items: RecommendationItem[]
}

interface RecommendationWire extends Omit<RecommendationItem, 'kind'> {
  kind: string
}

interface RecommendationPageWire {
  items: RecommendationWire[]
}

function normalizeKind(kind: string): RecommendationKind {
  if (kind === 'album') return 'album'
  if (kind === 'ep') return 'ep'
  if (kind === 'podcast' || kind === 'podcast-episode') return 'podcast'
  return 'single'
}

export async function apiRecommendations(limit = 12): Promise<RecommendationPage> {
  const page = await apiFetch<RecommendationPageWire>(`/me/recommendations?limit=${limit}`)
  return {
    items: page.items.map((item, index) => ({
      ...item,
      kind: normalizeKind(item.kind),
      recommendationId: item.recommendationId ?? null,
      rankingVersion: item.rankingVersion ?? 'legacy',
      position: item.position ?? index + 1,
    })),
  }
}

export type RecommendationEventType = 'impression' | 'open'
export type RecommendationFeedbackAction = 'not-interested' | 'dont-recommend-source'

export interface RecommendationEventInput {
  eventId: string
  recommendationId: string
  eventType: RecommendationEventType
}

export function apiRecordRecommendationEvents(events: RecommendationEventInput[]): Promise<void> {
  return apiFetch<void>('/me/recommendation-events', {
    method: 'POST',
    body: { events },
  })
}

export function apiRecommendationFeedback(
  recommendationId: string,
  action: RecommendationFeedbackAction,
): Promise<{ suppressionId: string }> {
  return apiFetch<{ suppressionId: string }>('/me/recommendation-feedback', {
    method: 'POST',
    body: { recommendationId, action },
  })
}

export function apiUndoRecommendationSuppression(suppressionId: string): Promise<void> {
  return apiFetch<void>(
    `/me/recommendation-suppressions/${encodeURIComponent(suppressionId)}`,
    { method: 'DELETE' },
  )
}

export function recommendationsQuery(enabled: boolean, limit = 12) {
  return queryOptions({
    queryKey: ['me', 'recommendations', limit],
    queryFn: () => apiRecommendations(limit),
    enabled,
    staleTime: 5 * 60 * 1000,
  })
}
