import { queryOptions } from '@tanstack/react-query'
import type { AnalyticsRange, AudienceRange } from '../../studio-analytics'
import { apiFetch } from '../client'
import { toAnalytics, toAudience, type AnalyticsWire, type AudienceWire } from '../mappers'

/**
 * `GET /v1/studio/analytics?range=` — the signed-in creator's analytics for a range.
 * The range is part of the query key so switching it refetches rather than reusing
 * the previous range's cached view.
 */
export function studioAnalyticsQuery(range: AnalyticsRange) {
  return queryOptions({
    queryKey: ['studio', 'analytics', range],
    queryFn: async () => toAnalytics(await apiFetch<AnalyticsWire>(`/studio/analytics?range=${range}`)),
  })
}

/** `GET /v1/studio/audience?range=` — the signed-in creator's audience for a range. */
export function studioAudienceQuery(range: AudienceRange) {
  return queryOptions({
    queryKey: ['studio', 'audience', range],
    queryFn: async () => toAudience(await apiFetch<AudienceWire>(`/studio/audience?range=${range}`)),
  })
}
