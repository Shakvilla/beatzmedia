import { useEffect, useRef } from 'react'
import { ChevronDown, Share2, Play, Pause, SkipBack, SkipForward, Mic2, ShoppingCart } from 'lucide-react'
import { cn } from '../../utils/cn'
import { usePlayer } from '../../features/player/player-context'
import { usePlaybackDisplay } from '../../features/player/use-playback-display'
import { UnavailableNotice } from '../../features/player/components/unavailable-notice'
import { useBuyTrack } from '../../features/cart/use-buy-track'
import { useToast } from '../ui/toast-provider'
import { useQuery } from '@tanstack/react-query'
import { activeLyricIndex } from '../../lib/lyrics-data'
import { lyricsQuery } from '../../lib/api/queries/catalog'
import { formatDuration, formatPrice } from '../../lib/format'
import { PREVIEW_ENDED_MESSAGE } from '../layout/player-bar'

interface LyricsViewProps {
  onClose?: () => void
  showCloseButton?: boolean
}

export function LyricsView({ onClose, showCloseButton = true }: LyricsViewProps) {
  const { currentTrack, isPlaying, progress, togglePlay, next, prev, isPreview, previewHitLimit } = usePlayer()
  const { effectiveDuration, progressRatio, unavailable, seekToRatio, seekToSeconds } = usePlaybackDisplay()
  const buyTrack = useBuyTrack()
  const { toast } = useToast()
  const activeRef = useRef<HTMLButtonElement>(null)

  /**
   * Real lyrics, from `GET /v1/tracks/:id/lyrics`.
   *
   * This called `getLyrics(trackId, duration)` from `lyrics-data.ts`, which returned a generic
   * block of placeholder lines evenly spread across the song's duration for any track without a
   * hardcoded entry — which is every track a real artist uploads. Because they were time-spread
   * they looked synced, so they did not read as missing data; they read as *this artist's lyrics*.
   *
   * The endpoint answers 404 for the many tracks that have none, and `lyricsQuery` maps that to an
   * empty list, so "no lyrics" now renders as no lyrics.
   */
  const { data: lines = [] } = useQuery({
    ...lyricsQuery(currentTrack?.id ?? ''),
    enabled: !!currentTrack,
  })
  const activeIndex = activeLyricIndex(lines, progress)

  // Auto-scroll the active line into the middle of the lyrics column.
  useEffect(() => {
    activeRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }, [activeIndex])

  if (!currentTrack) {
    return (
      <div className="relative w-full h-full min-h-screen flex flex-col items-center justify-center gap-4 bg-beatz-dark-bg text-white">
        {showCloseButton && (
          <button onClick={onClose} className="absolute top-8 left-8 p-2 hover:bg-white/10 rounded-full transition-colors">
            <ChevronDown size={24} />
          </button>
        )}
        <Mic2 size={40} className="text-white/40" />
        <p className="text-white/60">Nothing playing — start a track to see its lyrics.</p>
      </div>
    )
  }

  return (
    <div className="relative w-full h-full min-h-screen overflow-hidden flex flex-col animate-in fade-in duration-500 text-white">
      {/* Blurred-art background */}
      <div className="absolute inset-0 z-0">
        <img src={currentTrack.image} alt="" className="w-full h-full object-cover blur-3xl scale-125 opacity-50" />
        <div className="absolute inset-0 bg-gradient-to-b from-black/70 via-black/60 to-black/90" />
      </div>

      {/* Header */}
      <header className="relative z-20 flex items-center justify-between p-6 lg:p-8">
        <div className="flex items-center gap-4">
          {showCloseButton && (
            <button onClick={onClose} aria-label="Close lyrics" className="p-2 hover:bg-white/10 rounded-full transition-colors">
              <ChevronDown size={24} />
            </button>
          )}
          <div className="flex flex-col">
            <span className="text-[10px] font-bold tracking-[0.2em] uppercase text-white/60">Now Playing</span>
            <span className="text-xs font-bold tracking-widest uppercase">Lyrics</span>
          </div>
        </div>
        <button
          onClick={() => toast('Lyric link copied', 'success')}
          className="h-10 px-5 rounded-full border border-white/20 text-xs font-bold flex items-center gap-2 hover:bg-white/10 transition-colors"
        >
          <Share2 size={16} /> Share
        </button>
      </header>

      {/* Main */}
      <main className="relative z-10 flex-1 grid grid-cols-1 lg:grid-cols-2 gap-10 px-6 lg:px-16 pb-4 items-center max-w-7xl mx-auto w-full min-h-0">
        {/* Art + info (hidden on small to give lyrics room) */}
        <div className="hidden lg:flex flex-col items-start gap-6">
          <div className="w-full aspect-square max-w-[420px] rounded-2xl overflow-hidden shadow-2xl">
            <img src={currentTrack.image} alt={currentTrack.title} className="w-full h-full object-cover" />
          </div>
          <div className="flex flex-col gap-1">
            <h2 className="text-4xl font-bold tracking-tight">{currentTrack.title}</h2>
            <span className="text-lg font-medium text-white/60">
              {currentTrack.artistName}{currentTrack.albumTitle ? ` • ${currentTrack.albumTitle}` : ''}
            </span>
          </div>
        </div>

        {/* Synced lyrics */}
        <div className="flex flex-col gap-5 lg:gap-7 h-full max-h-[58vh] lg:max-h-[620px] overflow-y-auto no-scrollbar py-8 mask-fade-edges">
          {lines.length === 0 && (
            // Most tracks genuinely have no lyrics — the endpoint 404s and that maps to []. Saying
            // so is the point of this change: the placeholder block it replaced was indistinguishable
            // from real, time-synced words.
            <div className="flex flex-col items-center justify-center gap-3 h-full text-center">
              <Mic2 size={32} className="text-white/30" />
              <p className="text-white/50">No lyrics for this track yet.</p>
            </div>
          )}
          {lines.map((line, idx) => {
            const isActive = idx === activeIndex
            return (
              <button
                key={idx}
                ref={isActive ? activeRef : undefined}
                onClick={() => seekToSeconds(line.time)}
                className={cn(
                  'text-left font-bold origin-left transition-all duration-500',
                  isActive
                    ? 'text-3xl lg:text-5xl text-white scale-100'
                    : idx < activeIndex
                      ? 'text-2xl lg:text-3xl text-white/30 hover:text-white/60'
                      : 'text-2xl lg:text-3xl text-white/45 hover:text-white/70',
                )}
              >
                {line.text}
              </button>
            )
          })}
        </div>
      </main>

      {/* Transport */}
      <footer className="relative z-20 px-6 lg:px-16 pb-8 pt-2 max-w-7xl mx-auto w-full">
        {/* Mobile track info */}
        <div className="lg:hidden flex items-center gap-3 mb-4">
          <div className="w-12 h-12 rounded-md overflow-hidden shrink-0">
            <img src={currentTrack.image} alt={currentTrack.title} className="w-full h-full object-cover" />
          </div>
          <div className="flex flex-col min-w-0">
            <span className="font-bold truncate">{currentTrack.title}</span>
            <span className="text-sm text-white/60 truncate">{currentTrack.artistName}</span>
          </div>
        </div>

        {/* Seek */}
        {unavailable ? (
          <div className="flex items-center justify-center">
            <UnavailableNotice tone="dark" />
          </div>
        ) : (
          <div className="flex items-center gap-3">
            <span className="text-[10px] font-mono text-white/60 w-10">{formatDuration(progress)}</span>
            <div
              className="flex-1 h-1.5 bg-white/15 rounded-full overflow-hidden cursor-pointer group"
              onClick={(e) => {
                const rect = e.currentTarget.getBoundingClientRect()
                seekToRatio((e.clientX - rect.left) / rect.width)
              }}
            >
              <div className="h-full bg-beatz-green transition-[width] duration-200" style={{ width: `${progressRatio * 100}%` }} />
            </div>
            <span className="text-[10px] font-mono text-white/60 w-10 text-right">{formatDuration(effectiveDuration)}</span>
          </div>
        )}
        {(previewHitLimit || isPreview) && (
          // This route is full-screen, so PlayerBar — and with it the only Buy button in the app
          // chrome — is unmounted here. Telling the fan to buy without giving them anything to
          // buy with is a dead end, so the control ships alongside the message (I8).
          <div className="flex flex-wrap items-center justify-center gap-3 mt-2">
            {previewHitLimit && (
              <span className="text-xs font-bold text-[#f6c644]">{PREVIEW_ENDED_MESSAGE}</span>
            )}
            {isPreview && (
              <button
                onClick={() => buyTrack(currentTrack)}
                className="h-8 px-3 rounded-full bg-beatz-green text-black text-xs font-bold flex items-center gap-1.5 hover:scale-105 transition-transform"
              >
                <ShoppingCart size={13} /> Buy{currentTrack.price ? ` • ${formatPrice(currentTrack.price)}` : ''}
              </button>
            )}
          </div>
        )}

        {/* Controls */}
        <div className="flex items-center justify-center gap-8 mt-4">
          <button onClick={prev} aria-label="Previous" className="text-white/70 hover:text-white transition-colors"><SkipBack size={26} fill="currentColor" /></button>
          <button
            onClick={togglePlay}
            disabled={unavailable}
            aria-label={isPlaying ? 'Pause' : 'Play'}
            className="w-14 h-14 rounded-full bg-white text-black flex items-center justify-center hover:scale-105 transition-transform disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:scale-100"
          >
            {isPlaying ? <Pause size={26} fill="currentColor" /> : <Play size={26} fill="currentColor" className="ml-0.5" />}
          </button>
          <button onClick={next} aria-label="Next" className="text-white/70 hover:text-white transition-colors"><SkipForward size={26} fill="currentColor" /></button>
        </div>
      </footer>

      <style>{`
        .mask-fade-edges {
          -webkit-mask-image: linear-gradient(to bottom, transparent 0%, black 12%, black 88%, transparent 100%);
          mask-image: linear-gradient(to bottom, transparent 0%, black 12%, black 88%, transparent 100%);
        }
      `}</style>
    </div>
  )
}
