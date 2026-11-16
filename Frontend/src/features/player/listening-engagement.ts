import { getToken } from '../../lib/api/token'
import {
  apiPutListeningCheckpoint,
  apiStartListeningSession,
  type ListeningCheckpointInput,
  type ListeningContentKind,
  type ListeningEventType,
} from '../../lib/api/queries/engagement'
import type { Track } from '../../types'
import { takeRecommendationForTrack } from '../discovery/recommendation-attribution'

const PROGRESS_INTERVAL_SECONDS = 15
const QUALIFIED_PLAY_SECONDS = 30

interface ActiveListen {
  contentId: string
  contentKind: ListeningContentKind
  durationSeconds: number
  positionSeconds: number
  listenedSinceCheckpoint: number
  totalListenedSeconds: number
  qualified: boolean
  completed: boolean
  recommendationId: string | null
}

/**
 * Best-effort engagement recorder. Playback never awaits this class: durable history is useful,
 * but it is deliberately outside the stream, ownership, play-count, and royalty paths.
 */
export class ListeningEngagementRecorder {
  private sessionPromise: ReturnType<typeof apiStartListeningSession> | null = null
  private sessionId: string | null = null
  private sessionToken: string | null = null
  private recordingDisabled = false
  private sequence = 0
  private active: ActiveListen | null = null

  start(track: Track, durationSeconds: number): void {
    if (!getToken()) return
    const contentKind: ListeningContentKind =
      track.playbackKind === 'podcast_episode' ? 'podcast-episode' : 'track'

    if (this.active?.contentId === track.id && !this.active.completed) return
    if (this.recordingDisabled) {
      // Re-check the preference once per new item, so enabling history takes effect without reload.
      this.sessionPromise = null
      this.recordingDisabled = false
    }
    this.active = {
      contentId: track.id,
      contentKind,
      durationSeconds: this.safeSeconds(durationSeconds || track.duration),
      positionSeconds: 0,
      listenedSinceCheckpoint: 0,
      totalListenedSeconds: 0,
      qualified: false,
      completed: false,
      recommendationId: takeRecommendationForTrack(track),
    }
    this.send('start', 0)
  }

  progress(positionSeconds: number, durationSeconds: number): void {
    const active = this.active
    if (!active) return
    const position = this.safeSeconds(positionSeconds)
    const previous = active.positionSeconds
    const elapsed = position > previous && position - previous <= 5 ? position - previous : 0
    active.positionSeconds = position
    active.durationSeconds = this.safeSeconds(durationSeconds || active.durationSeconds)
    active.listenedSinceCheckpoint += elapsed
    active.totalListenedSeconds += elapsed

    if (!active.qualified && active.totalListenedSeconds >= QUALIFIED_PLAY_SECONDS) {
      active.qualified = true
      this.send('qualified-play', active.listenedSinceCheckpoint)
      active.listenedSinceCheckpoint = 0
      return
    }
    if (active.listenedSinceCheckpoint >= PROGRESS_INTERVAL_SECONDS) {
      this.send('progress', active.listenedSinceCheckpoint)
      active.listenedSinceCheckpoint = 0
    }
  }

  skip(): void {
    if (!this.active || this.active.completed) return
    this.send('skip', this.active.listenedSinceCheckpoint)
    this.active = null
  }

  complete(repeating: boolean): void {
    if (!this.active) return
    this.active.completed = true
    this.send('completion', this.active.listenedSinceCheckpoint)
    if (repeating) {
      this.send('repeat', 0)
      this.active.completed = false
      this.active.positionSeconds = 0
      this.active.listenedSinceCheckpoint = 0
      this.active.totalListenedSeconds = 0
      this.active.qualified = false
    }
  }

  reset(): void {
    this.active = null
    this.sessionPromise = null
    this.sessionId = null
    this.sessionToken = null
    this.recordingDisabled = false
    this.sequence = 0
  }

  private send(eventType: ListeningEventType, listenedDeltaSeconds: number): void {
    const snapshot = this.active
    if (!snapshot) return
    const input: ListeningCheckpointInput = {
      contentKind: snapshot.contentKind,
      contentId: snapshot.contentId,
      eventType,
      positionSeconds: this.safeSeconds(snapshot.positionSeconds),
      durationSeconds: this.safeSeconds(snapshot.durationSeconds),
      listenedDeltaSeconds: this.safeSeconds(listenedDeltaSeconds),
      ...(snapshot.recommendationId
        ? { recommendationId: snapshot.recommendationId }
        : {}),
    }
    void this.ensureSession()
      .then((sessionId) => {
        if (!sessionId) return
        const sequence = ++this.sequence
        return apiPutListeningCheckpoint(sessionId, sequence, input)
      })
      .catch(() => {
        // Engagement availability must never affect audio playback.
      })
  }

  private async ensureSession(): Promise<string | null> {
    const token = getToken()
    if (!token) {
      this.reset()
      return null
    }
    if (this.sessionToken !== token) {
      this.sessionPromise = null
      this.sessionId = null
      this.sequence = 0
      this.recordingDisabled = false
      this.sessionToken = token
    }
    if (this.sessionId) return this.sessionId
    this.sessionPromise ??= apiStartListeningSession()
    const session = await this.sessionPromise
    this.sessionId = session.recording ? session.sessionId : null
    this.recordingDisabled = !session.recording
    return this.sessionId
  }

  private safeSeconds(value: number): number {
    return Number.isFinite(value) ? Math.max(0, Math.round(value)) : 0
  }
}
