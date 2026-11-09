import type { RecommendationItem } from '../../lib/api/queries/recommendations'
import type { Track } from '../../types'

const STORAGE_KEY = 'beatzclik.recommendation-attribution'
const MAX_AGE_MS = 30 * 60 * 1000

interface StoredAttribution {
  recommendationId: string
  kind: RecommendationItem['kind']
  contentId: string
  creatorId: string
  storedAt: number
}

export function rememberRecommendation(item: RecommendationItem): void {
  if (!item.recommendationId || typeof sessionStorage === 'undefined') return
  const value: StoredAttribution = {
    recommendationId: item.recommendationId,
    kind: item.kind,
    contentId: item.contentId,
    creatorId: item.creatorId,
    storedAt: Date.now(),
  }
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(value))
}

export function takeRecommendationForTrack(track: Track): string | null {
  const stored = read()
  if (!stored || !matches(stored, track)) return null
  sessionStorage.removeItem(STORAGE_KEY)
  return stored.recommendationId
}

function read(): StoredAttribution | null {
  if (typeof sessionStorage === 'undefined') return null
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) return null
  try {
    const value = JSON.parse(raw) as StoredAttribution
    if (!value.recommendationId || Date.now() - value.storedAt > MAX_AGE_MS) {
      sessionStorage.removeItem(STORAGE_KEY)
      return null
    }
    return value
  } catch {
    sessionStorage.removeItem(STORAGE_KEY)
    return null
  }
}

function matches(attribution: StoredAttribution, track: Track): boolean {
  if (attribution.kind === 'single') return track.id === attribution.contentId
  if (attribution.kind === 'album' || attribution.kind === 'ep') {
    return track.playbackKind !== 'podcast_episode' && track.albumId === attribution.contentId
  }
  return track.playbackKind === 'podcast_episode' && track.artistId === attribution.creatorId
}
