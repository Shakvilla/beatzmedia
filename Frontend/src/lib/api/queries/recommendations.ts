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
  return { items: page.items.map((item) => ({ ...item, kind: normalizeKind(item.kind) })) }
}

export function recommendationsQuery(enabled: boolean, limit = 12) {
  return queryOptions({
    queryKey: ['me', 'recommendations', limit],
    queryFn: () => apiRecommendations(limit),
    enabled,
    staleTime: 5 * 60 * 1000,
  })
}
