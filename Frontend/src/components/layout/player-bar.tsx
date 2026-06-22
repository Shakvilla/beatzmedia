import { useEffect } from "react"
import { Play, Pause, SkipBack, SkipForward, Volume2, VolumeX, ListMusic, Shuffle, Repeat, Repeat1, Lock, ShoppingCart } from "lucide-react"
import { Link } from "@tanstack/react-router"
import { usePlayer } from "../../features/player/player-context"
import { useCart } from "../../features/cart/cart-context"
import { useToast } from "../ui/toast-provider"
import { formatDuration, formatPrice } from "../../lib/format"
import { cn } from "../../utils/cn"
import { Tooltip } from "../ui/tooltip"

/** Click-to-seek / click-to-set bar. `value` and `max` are in the same unit. */
function ProgressTrack({
  value,
  max,
  onScrub,
  className = "",
  fillClass = "bg-beatz-green",
}: {
  value: number
  max: number
  onScrub?: (ratio: number) => void
  className?: string
  fillClass?: string
}) {
  const ratio = max > 0 ? Math.min(1, value / max) : 0
  return (
    <div
      className={`relative h-1.5 bg-gray-200 dark:bg-beatz-dark-surface-2 rounded-full overflow-hidden group ${onScrub ? "cursor-pointer" : ""} ${className}`}
      onClick={(e) => {
        if (!onScrub) return
        const rect = e.currentTarget.getBoundingClientRect()
        onScrub((e.clientX - rect.left) / rect.width)
      }}
    >
      <div className={`h-full ${fillClass} transition-[width] duration-200 relative`} style={{ width: `${ratio * 100}%` }}>
        <div className="absolute right-0 top-1/2 -translate-y-1/2 w-3 h-3 bg-white rounded-full opacity-0 group-hover:opacity-100 shadow-md" />
      </div>
    </div>
  )
}

export function PlayerBar() {
  const { currentTrack, isPlaying, progress, volume, shuffle, repeat, isPreview, previewSeconds, previewHitLimit, togglePlay, next, prev, seek, setVolume, toggleQueue, toggleShuffle, cycleRepeat } = usePlayer()
  const { addItem } = useCart()
  const { toast } = useToast()
  const progressRatio = currentTrack?.duration ? Math.min(1, progress / currentTrack.duration) : 0
  /** Cap scrubbing to the preview window for unowned for-sale tracks. */
  const seekCap = (ratio: number) => {
    if (!currentTrack) return
    const target = ratio * currentTrack.duration
    seek(isPreview ? Math.min(target, previewSeconds) : target)
  }

  const buyCurrent = () => {
    if (!currentTrack) return
    addItem({
      id: `track:${currentTrack.id}`,
      kind: 'track',
      title: currentTrack.title,
      subtitle: currentTrack.artistName,
      image: currentTrack.image,
      price: currentTrack.price ?? { amount: 0, currency: 'GHS' },
    })
    toast(`“${currentTrack.title}” added to cart`, 'success')
  }

  // Nudge the listener to buy when a preview runs out.
  useEffect(() => {
    if (previewHitLimit) toast('Preview ended — buy to keep listening', 'info')
  }, [previewHitLimit, toast])

  return (
    <footer className="absolute bottom-16 md:bottom-0 left-0 right-0 h-20 md:h-24 bg-white/90 dark:bg-[#181818]/95 backdrop-blur-xl border-t border-gray-200 dark:border-white/5 px-4 md:px-6 flex items-center justify-between z-40 transition-colors duration-300">
      {/* Mobile-only thin progress line at the very top */}
      <div className="md:hidden absolute top-0 left-0 right-0 h-0.5 bg-gray-200 dark:bg-white/10">
        <div className="h-full bg-beatz-green transition-[width] duration-200" style={{ width: `${progressRatio * 100}%` }} />
      </div>

      <div className="flex items-center gap-2 md:gap-3 flex-1 md:w-1/3 min-w-0">
        <Link to="/lyrics" className="flex items-center gap-3 md:gap-4 min-w-0 flex-1 hover:opacity-80 transition-opacity cursor-pointer">
          <div className="w-12 h-12 md:w-14 md:h-14 bg-gray-200 dark:bg-beatz-dark-surface-2 rounded-md overflow-hidden shrink-0">
            {currentTrack && (
              <img src={currentTrack.image} alt={currentTrack.title} className="w-full h-full object-cover" />
            )}
          </div>
          <div className="flex flex-col min-w-0">
            <span className="flex items-center gap-1.5 min-w-0">
              <span className="text-body-strong truncate">{currentTrack?.title ?? "Nothing playing"}</span>
              {isPreview && (
                <span className="hidden sm:inline-flex items-center gap-1 text-[9px] font-bold uppercase tracking-wider text-[#b8881f] dark:text-[#f6c644] bg-[#f6c644]/20 px-1.5 py-0.5 rounded shrink-0">
                  <Lock size={9} /> Preview
                </span>
              )}
            </span>
            <span className="text-xs text-gray-500 dark:text-beatz-light-surface-3 truncate">
              {currentTrack?.artistName ?? "Pick a track to get started"}
            </span>
          </div>
        </Link>
        {isPreview && currentTrack && (
          <button
            onClick={buyCurrent}
            className="shrink-0 h-8 px-3 rounded-full bg-beatz-green text-black text-xs font-bold flex items-center gap-1.5 hover:scale-105 transition-transform"
          >
            <ShoppingCart size={13} /> <span className="hidden sm:inline">{currentTrack.price ? formatPrice(currentTrack.price) : 'Buy'}</span>
          </button>
        )}
      </div>

      {/* Mobile transport — compact, on the right */}
      <div className="flex md:hidden items-center gap-1 shrink-0">
        <button onClick={togglePlay} aria-label={isPlaying ? "Pause" : "Play"} className="w-10 h-10 flex items-center justify-center text-beatz-dark-bg dark:text-white">
          {isPlaying ? <Pause size={26} fill="currentColor" /> : <Play size={26} fill="currentColor" className="ml-0.5" />}
        </button>
        <button onClick={next} aria-label="Next" className="w-9 h-9 flex items-center justify-center text-gray-500 dark:text-gray-300">
          <SkipForward size={20} fill="currentColor" />
        </button>
      </div>

      {/* Desktop transport + seek */}
      <div className="hidden md:flex flex-col items-center gap-2 w-1/3">
        <div className="flex items-center gap-5">
          <Tooltip label={shuffle ? 'Shuffle: on' : 'Shuffle'}>
            <button
              onClick={toggleShuffle}
              aria-label="Shuffle"
              className={cn('transition-colors', shuffle ? 'text-beatz-green' : 'text-gray-400 hover:text-black dark:hover:text-white')}
            >
              <Shuffle size={17} />
            </button>
          </Tooltip>
          <button onClick={prev} aria-label="Previous">
            <SkipBack className="text-gray-400 hover:text-black dark:hover:text-white cursor-pointer transition-colors" size={20} />
          </button>
          <button
            onClick={togglePlay}
            aria-label={isPlaying ? "Pause" : "Play"}
            className="w-10 h-10 bg-black dark:bg-white rounded-full flex items-center justify-center hover:scale-105 transition-transform cursor-pointer"
          >
            {isPlaying ? (
              <Pause className="text-white dark:text-black" size={20} fill="currentColor" />
            ) : (
              <Play className="text-white dark:text-black ml-0.5" size={20} fill="currentColor" />
            )}
          </button>
          <button onClick={next} aria-label="Next">
            <SkipForward className="text-gray-400 hover:text-black dark:hover:text-white cursor-pointer transition-colors" size={20} />
          </button>
          <Tooltip label={repeat === 'all' ? 'Repeat: all' : repeat === 'one' ? 'Repeat: one' : 'Repeat'}>
            <button
              onClick={cycleRepeat}
              aria-label={`Repeat: ${repeat}`}
              className={cn('transition-colors', repeat !== 'off' ? 'text-beatz-green' : 'text-gray-400 hover:text-black dark:hover:text-white')}
            >
              {repeat === 'one' ? <Repeat1 size={17} /> : <Repeat size={17} />}
            </button>
          </Tooltip>
        </div>
        <div className="w-full max-w-md flex items-center gap-3">
          <span className="text-[10px] text-gray-400 font-mono">{formatDuration(progress)}</span>
          <ProgressTrack
            className="flex-1"
            value={progress}
            max={currentTrack?.duration ?? 0}
            onScrub={seekCap}
          />
          <span className="text-[10px] text-gray-400 font-mono">{formatDuration(currentTrack?.duration ?? 0)}</span>
        </div>
      </div>

      <div className="hidden md:flex items-center justify-end gap-5 w-1/3">
        <button
          onClick={toggleQueue}
          aria-label="Toggle queue"
          className="text-gray-500 dark:text-gray-300 hover:text-beatz-green transition-colors"
        >
          <ListMusic size={20} />
        </button>
        <div className="flex items-center gap-3">
          <button onClick={() => setVolume(volume > 0 ? 0 : 0.7)} aria-label="Mute">
            {volume > 0 ? (
              <Volume2 size={20} className="text-gray-500 dark:text-gray-300" />
            ) : (
              <VolumeX size={20} className="text-gray-500 dark:text-gray-300" />
            )}
          </button>
          <ProgressTrack
            className="w-24"
            value={volume}
            max={1}
            onScrub={(r) => setVolume(r)}
            fillClass="bg-gray-400 dark:bg-white group-hover:bg-beatz-green"
          />
        </div>
      </div>
    </footer>
  )
}
