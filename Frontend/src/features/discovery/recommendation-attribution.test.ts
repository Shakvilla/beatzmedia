import { beforeEach, describe, expect, it } from 'vitest'
import type { RecommendationItem } from '../../lib/api/queries/recommendations'
import type { Track } from '../../types'
import { rememberRecommendation, takeRecommendationForTrack } from './recommendation-attribution'

const baseRecommendation: RecommendationItem = {
  kind: 'album',
  contentId: 'album-1',
  creatorId: 'artist-1',
  title: 'Album',
  subtitle: 'Artist',
  image: null,
  route: '/album/album-1',
  reason: 'Popular on BeatzClik',
  recommendationId: 'recommendation-1',
  rankingVersion: 'rules-v1',
  position: 1,
}

const baseTrack: Track = {
  id: 'track-1',
  title: 'Track',
  artistId: 'artist-1',
  artistName: 'Artist',
  albumId: 'album-1',
  duration: 180,
  image: '/cover',
  ownership: 'free',
  downloadable: false,
}

describe('recommendation attribution context', () => {
  beforeEach(() => sessionStorage.clear())

  it('carries an album recommendation to the first matching track exactly once', () => {
    rememberRecommendation(baseRecommendation)

    expect(takeRecommendationForTrack(baseTrack)).toBe('recommendation-1')
    expect(takeRecommendationForTrack(baseTrack)).toBeNull()
  })

  it('does not consume attribution for unrelated playback', () => {
    rememberRecommendation(baseRecommendation)

    expect(takeRecommendationForTrack({ ...baseTrack, albumId: 'album-2' })).toBeNull()
    expect(takeRecommendationForTrack(baseTrack)).toBe('recommendation-1')
  })

  it('matches podcast recommendations through the episode show identity', () => {
    rememberRecommendation({
      ...baseRecommendation,
      kind: 'podcast',
      contentId: 'show-1',
      creatorId: 'show-1',
    })

    expect(takeRecommendationForTrack({
      ...baseTrack,
      playbackKind: 'podcast_episode',
      artistId: 'show-1',
    })).toBe('recommendation-1')
  })
})
