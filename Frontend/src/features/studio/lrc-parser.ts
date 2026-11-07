import type { LyricLineWire } from '../../lib/api/mappers'

const MAX_FILE_BYTES = 1_048_576
const MAX_LINES = 1000
const MAX_LINE_LENGTH = 500
const TIMESTAMP = /\[(\d{1,3}):([0-5]\d)(?:[.,](\d{1,3}))?\]/g

/** Parse standard line-timed LRC text into the API's seconds-based lyric shape. */
export function parseLrc(source: string): LyricLineWire[] {
  if (new TextEncoder().encode(source).byteLength > MAX_FILE_BYTES) {
    throw new Error('LRC files must be 1 MB or smaller.')
  }

  const sourceLines = source.replace(/^\uFEFF/, '').split(/\r?\n/)
  let offsetMs = 0
  for (const line of sourceLines) {
    const offset = /^\s*\[offset:([+-]?\d+)\]\s*$/i.exec(line)
    if (offset) offsetMs = Number(offset[1])
  }

  const timedLines: { timeMs: number; text: string; order: number }[] = []
  let order = 0
  for (let lineIndex = 0; lineIndex < sourceLines.length; lineIndex++) {
    const line = sourceLines[lineIndex]?.trim() ?? ''
    if (!line || /^\[offset:[+-]?\d+\]$/i.test(line)) continue

    TIMESTAMP.lastIndex = 0
    const matches = [...line.matchAll(TIMESTAMP)]
    if (matches.length === 0) {
      // Common LRC metadata (title, artist, album, tool, version, etc.) is not lyric text.
      if (/^\[[a-z][a-z0-9_-]*:.*\]$/i.test(line)) continue
      throw new Error(`Line ${lineIndex + 1} is not a timestamped LRC lyric or metadata line.`)
    }
    if (matches[0]?.index !== 0) {
      throw new Error(`Line ${lineIndex + 1} must start with an LRC timestamp.`)
    }

    const text = line.replace(TIMESTAMP, '').trim()
    if (!text) continue
    if (text.length > MAX_LINE_LENGTH) {
      throw new Error(`Lyric line ${lineIndex + 1} is longer than 500 characters.`)
    }

    for (const match of matches) {
      const minutes = Number(match[1])
      const seconds = Number(match[2])
      const fractionMs = Number((match[3] ?? '').padEnd(3, '0'))
      const timeMs = minutes * 60_000 + seconds * 1000 + fractionMs + offsetMs
      if (timeMs < 0) {
        throw new Error(`The LRC offset makes the timestamp on line ${lineIndex + 1} negative.`)
      }
      timedLines.push({ timeMs, text, order: order++ })
      if (timedLines.length > MAX_LINES) {
        throw new Error('Lyrics may contain at most 1,000 timestamped lines.')
      }
    }
  }

  if (timedLines.length === 0) throw new Error('This LRC file contains no timestamped lyric lines.')

  return timedLines
    .sort((a, b) => a.timeMs - b.timeMs || a.order - b.order)
    .map(({ timeMs, text }) => ({ time: timeMs / 1000, text }))
}
