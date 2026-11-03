import { useEffect, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Mic2, Save } from 'lucide-react'
import { useToast } from '../../components/ui/toast-provider'
import { lyricsQuery } from '../../lib/api/queries/catalog'
import { apiSaveTrackLyrics, studioTrackLyricsQuery } from '../../lib/api/queries/studio'
import type { LyricLineWire } from '../../lib/api/mappers'

interface TrackLyricsEditorProps {
  releaseId: string
  trackId: string
  trackTitle: string
}

const TEXTAREA = 'w-full min-h-36 rounded-xl bg-white dark:bg-white/5 border border-gray-200 dark:border-white/10 px-4 py-3 text-sm text-beatz-dark-bg dark:text-white placeholder:text-gray-400 dark:placeholder:text-white/25 focus:outline-none focus:border-beatz-green/60 focus:ring-1 focus:ring-beatz-green/30 transition-all resize-y'

function formatLyrics(lines: LyricLineWire[]): string {
  return lines.map((line) => {
    if (line.time === null) return line.text
    const minutes = Math.floor(line.time / 60).toString().padStart(2, '0')
    const seconds = (line.time % 60).toString().padStart(2, '0')
    return `[${minutes}:${seconds}] ${line.text}`
  }).join('\n')
}

function parseLyrics(text: string): LyricLineWire[] {
  const sourceLines = text.split(/\r?\n/).map((line) => line.trim()).filter(Boolean)
  if (sourceLines.length === 0) return []

  const timedPattern = /^\[(\d{1,3}):([0-5]\d)\]\s+(.+)$/
  const parsed = sourceLines.map((line) => {
    const match = timedPattern.exec(line)
    if (!match) return { time: null, text: line }
    return { time: Number(match[1]) * 60 + Number(match[2]), text: match[3]?.trim() ?? '' }
  })
  const timedCount = parsed.filter((line) => line.time !== null).length
  if (timedCount > 0 && timedCount !== parsed.length) {
    throw new Error('For synced lyrics, add a [mm:ss] timestamp to every line. Otherwise remove timestamps from all lines.')
  }
  if (parsed.length > 1000) throw new Error('Lyrics may contain at most 1,000 lines.')
  if (parsed.some((line) => !line.text || line.text.length > 500)) {
    throw new Error('Each lyric line must contain 1 to 500 characters.')
  }
  return parsed
}

export function TrackLyricsEditor({ releaseId, trackId, trackTitle }: TrackLyricsEditorProps) {
  const { data: lines, isPending, isError } = useQuery(studioTrackLyricsQuery(releaseId, trackId))
  const queryClient = useQueryClient()
  const { toast } = useToast()
  const [text, setText] = useState('')
  const [saving, setSaving] = useState(false)
  const [parseError, setParseError] = useState<string | null>(null)

  useEffect(() => {
    if (lines) setText(formatLyrics(lines))
  }, [lines])

  const save = async () => {
    let parsed: LyricLineWire[]
    try {
      parsed = parseLyrics(text)
      setParseError(null)
    } catch (error) {
      setParseError(error instanceof Error ? error.message : 'Check the lyric formatting.')
      return
    }

    setSaving(true)
    try {
      await apiSaveTrackLyrics(releaseId, trackId, parsed)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: studioTrackLyricsQuery(releaseId, trackId).queryKey }),
        queryClient.invalidateQueries({ queryKey: lyricsQuery(trackId).queryKey }),
      ])
      toast(parsed.length ? 'Lyrics published immediately' : 'Lyrics removed', 'success')
    } catch (error) {
      toast(error instanceof Error ? error.message : 'Could not publish lyrics.', 'error')
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="mt-3 rounded-xl border border-gray-200 dark:border-white/10 bg-gray-50/70 dark:bg-white/[0.03] p-4 flex flex-col gap-3" aria-label={`${trackTitle} lyrics`}>
      <div className="flex items-start justify-between gap-3 flex-wrap">
        <div className="flex items-center gap-2">
          <Mic2 size={15} className="text-beatz-green" />
          <h3 className="text-sm font-bold text-beatz-dark-bg dark:text-white">Lyrics</h3>
          <span className="text-[10px] uppercase tracking-wider text-beatz-green font-bold">Publishes immediately</span>
        </div>
        <button type="button" onClick={save} disabled={isPending || isError || saving} className="h-9 px-4 rounded-full bg-beatz-green text-black text-xs font-bold inline-flex items-center gap-2 disabled:opacity-40">
          <Save size={13} /> {saving ? 'Publishing…' : text.trim() ? 'Publish lyrics' : 'Remove lyrics'}
        </button>
      </div>
      <label className="sr-only" htmlFor={`lyrics-${trackId}`}>Lyrics for {trackTitle}</label>
      <textarea
        id={`lyrics-${trackId}`}
        value={text}
        onChange={(event) => { setText(event.target.value); setParseError(null) }}
        disabled={isPending || isError || saving}
        placeholder={'Paste plain lyrics, one line per row.\n\nFor synced lyrics, prefix every line with a timestamp, for example:\n[00:12] First lyric line\n[00:18] Next lyric line'}
        className={TEXTAREA}
        aria-describedby={`lyrics-help-${trackId}`}
      />
      <p id={`lyrics-help-${trackId}`} className="text-xs text-gray-500 dark:text-gray-400">
        Plain text displays as a readable block. Add a [mm:ss] timestamp to every line to sync lyrics with playback. Lyrics are never held for review; on a draft release, they become public when the release goes live.
      </p>
      {isPending && <p role="status" className="text-xs text-gray-500">Loading saved lyrics…</p>}
      {isError && <p role="alert" className="text-xs text-beatz-red">Could not load lyrics. Refresh this release and try again.</p>}
      {parseError && <p role="alert" className="text-xs text-beatz-red">{parseError}</p>}
    </section>
  )
}
