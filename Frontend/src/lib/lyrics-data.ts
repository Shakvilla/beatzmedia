/** Line shape shared by the lyrics playback helpers. */

export interface LyricLine {
  /** Seconds into the track when this line starts. */
  time: number | null
  text: string
}

/** Index of the currently-active line for a given playback position. */
export function activeLyricIndex(lines: LyricLine[], progress: number): number {
  let idx = 0
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    if (line && line.time !== null && line.time <= progress) idx = i
    else break
  }
  return idx
}
