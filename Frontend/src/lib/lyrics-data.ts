/**
 * Mock time-synced lyrics. `time` is seconds into the track. Lines are original
 * placeholder lyrics (not the real songs' words) so the synced-lyrics UI can be
 * demoed without reproducing copyrighted text. Tracks without specific lyrics
 * fall back to a generic set spread across the track's duration.
 */

export interface LyricLine {
  /** Seconds into the track when this line starts. */
  time: number
  text: string
}

const SPECIFIC: Record<string, LyricLine[]> = {
  'last-last': [
    { time: 0, text: '♪' },
    { time: 6, text: 'No place feels the same since you left' },
    { time: 12, text: 'City lights, but the night feels cold' },
    { time: 19, text: 'I been moving, I been running solo' },
    { time: 26, text: 'Counting every mile on the road' },
    { time: 34, text: 'Last last, everybody go feel it' },
    { time: 41, text: 'Last last, na so the story go' },
    { time: 49, text: 'Tell them say I dey alright now' },
    { time: 57, text: 'Even when the morning slow' },
    { time: 66, text: '♪' },
    { time: 74, text: 'Hold on, the sun dey come' },
    { time: 82, text: 'Hold on, we no go run' },
  ],
  'kwaku-the-traveller': [
    { time: 0, text: '♪' },
    { time: 7, text: 'Packed my bags before the sunrise' },
    { time: 14, text: 'Konongo boy, but the world is wide' },
    { time: 22, text: 'Every wrong turn taught me something' },
    { time: 30, text: 'Now I carry it all with pride' },
    { time: 39, text: 'Who never make mistake before?' },
    { time: 47, text: 'Raise your hand if your heart is pure' },
    { time: 56, text: 'I traveled far to find my sound' },
    { time: 64, text: 'And I am never coming down' },
  ],
}

const GENERIC: string[] = [
  '♪',
  'Turn the volume up a little louder',
  'Feel the rhythm move through the crowd',
  'Every beat a story we are telling',
  'From the motherland, singing loud',
  'Hold the moment, let it carry on',
  'Dance until the morning comes',
  'This one is for the ones back home',
  'Wherever the music goes, we belong',
  '♪',
]

/** Returns time-synced lyric lines for a track (specific or generic fallback). */
export function getLyrics(trackId: string, duration: number): LyricLine[] {
  if (SPECIFIC[trackId]) return SPECIFIC[trackId]
  const n = GENERIC.length
  return GENERIC.map((text, i) => ({ time: Math.round((i / n) * duration), text }))
}

/** Index of the currently-active line for a given playback position. */
export function activeLyricIndex(lines: LyricLine[], progress: number): number {
  let idx = 0
  for (let i = 0; i < lines.length; i++) {
    if (lines[i].time <= progress) idx = i
    else break
  }
  return idx
}
