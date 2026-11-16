import {
  apiRecordRecommendationEvents,
  type RecommendationEventInput,
  type RecommendationEventType,
} from '../../lib/api/queries/recommendations'

const FLUSH_DELAY_MS = 250
const MAX_BATCH_SIZE = 50

const pending: RecommendationEventInput[] = []
let flushTimer: ReturnType<typeof setTimeout> | null = null

export function queueRecommendationEvent(
  recommendationId: string | null,
  eventType: RecommendationEventType,
): void {
  if (!recommendationId) return
  pending.push({ eventId: eventId(), recommendationId, eventType })
  if (pending.length >= MAX_BATCH_SIZE) {
    void flushRecommendationEvents()
    return
  }
  flushTimer ??= setTimeout(() => void flushRecommendationEvents(), FLUSH_DELAY_MS)
}

export async function flushRecommendationEvents(): Promise<void> {
  if (flushTimer) clearTimeout(flushTimer)
  flushTimer = null
  if (!pending.length) return
  const batch = pending.splice(0, MAX_BATCH_SIZE)
  try {
    await apiRecordRecommendationEvents(batch)
  } catch {
    // Attribution is best-effort and must never interrupt navigation or playback.
  }
  if (pending.length) flushTimer = setTimeout(() => void flushRecommendationEvents(), 0)
}

function eventId(): string {
  return globalThis.crypto?.randomUUID?.()
    ?? `recommendation-${Date.now()}-${Math.random().toString(36).slice(2)}`
}
