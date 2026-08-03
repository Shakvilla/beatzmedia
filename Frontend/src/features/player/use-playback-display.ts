/**
 * Everything a transport surface needs to render playback truthfully, in one place.
 *
 * There are three of these surfaces (the player bar, the lyrics view and the track page) and the
 * same four decisions were hand-copied into each of them: which duration is authoritative, what
 * the progress ratio is, that the bar must never fill while the stream is unavailable, and that a
 * seek must be clamped to the preview window the server signed. Copying them is exactly how the
 * second and third surfaces drifted out of sync with the first — so they live here now.
 */

import { usePlayer } from './player-context'
import type { Track } from '../../types'

export interface PlaybackDisplay {
  /** True when the player's current track is the one this surface is about. */
  isCurrent: boolean
  /** True when this surface's track is loaded AND playing. */
  isPlaying: boolean
  /**
   * Authoritative length in seconds. The audio element's own duration wins once metadata has
   * loaded, because catalogue duration disagrees with it — a 30s preview of a 3-minute track is
   * the whole point of INV-3. Falls back to catalogue duration before metadata, and for a track
   * that isn't loaded at all.
   */
  effectiveDuration: number
  /** Position to render; 0 when this surface's track isn't the one loaded. */
  progress: number
  /** 0..1 fill. Always 0 while `unavailable` — never animate a bar over silence. */
  progressRatio: number
  /** True when this surface must disable its transport and say the stream isn't playable. */
  unavailable: boolean
  /** True while the recovery refetch behind `retry` is in flight. */
  retrying: boolean
  /** Re-sign the stream and resume. The only way out of `unavailable`. */
  retry: () => void
  /** Length of the signed preview when the server clipped one; null for a full stream. */
  previewSeconds: number | null
  /** Seek to a 0..1 position on this surface's bar, clamped to the preview window. */
  seekToRatio: (ratio: number) => void
  /** Seek to an absolute second, clamped to the preview window. */
  seekToSeconds: (seconds: number) => void
}

/**
 * @param track the track this surface is about. Omit on surfaces that always describe whatever is
 *   currently loaded (player bar, lyrics view); pass it on a page that shows one specific track,
 *   so its transport goes quiet when that track isn't the one playing.
 */
export function usePlaybackDisplay(track?: Track | null): PlaybackDisplay {
  const {
    currentTrack,
    isPlaying,
    progress,
    duration,
    previewSeconds,
    unavailable,
    retrying,
    retry,
    seek,
  } = usePlayer()

  const isCurrent = track ? currentTrack?.id === track.id : !!currentTrack
  const subject = track ?? currentTrack

  const effectiveDuration = (isCurrent ? duration : null) ?? subject?.duration ?? 0
  const displayProgress = isCurrent ? progress : 0
  const surfaceUnavailable = isCurrent && unavailable
  const progressRatio =
    !surfaceUnavailable && effectiveDuration > 0
      ? Math.min(1, displayProgress / effectiveDuration)
      : 0

  // A preview is a genuinely shorter file. Seeking past its end lands after EOF, where the browser
  // clamps and fires `ended` — a silent stop with nothing said. Clamp instead.
  const seekToSeconds = (seconds: number) => {
    seek(previewSeconds != null ? Math.min(seconds, previewSeconds) : seconds)
  }
  const seekToRatio = (ratio: number) => seekToSeconds(ratio * effectiveDuration)

  return {
    isCurrent,
    isPlaying: isCurrent && isPlaying,
    effectiveDuration,
    progress: displayProgress,
    progressRatio,
    unavailable: surfaceUnavailable,
    retrying,
    retry,
    previewSeconds: isCurrent ? previewSeconds : null,
    seekToRatio,
    seekToSeconds,
  }
}
