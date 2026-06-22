import { Play, Pause, Plus, Share2, Lock, Check } from 'lucide-react'
import type { PodcastEpisode } from '../../../types'
import { formatTotalDuration, formatPrice } from '../../../lib/format'
import { episodeAccessible } from '../../../lib/podcast-data'
import { cn } from '../../../utils/cn'

interface EpisodeRowProps {
  episode: PodcastEpisode
  isCurrent: boolean
  isPlaying: boolean
  onPlay: () => void
  onBuy?: (episode: PodcastEpisode) => void
}

function relativeDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })
}

export function EpisodeRow({ episode, isCurrent, isPlaying, onPlay, onBuy }: EpisodeRowProps) {
  const accessible = episodeAccessible(episode)
  const locked = !accessible

  return (
    <div className="flex items-center gap-4 p-3 rounded-xl hover:bg-gray-100 dark:hover:bg-white/5 transition-colors group">
      <div className="relative w-14 h-14 rounded-lg overflow-hidden shrink-0 bg-beatz-light-surface-2 dark:bg-beatz-dark-surface-3">
        <img src={episode.image} alt={episode.title} className={cn('w-full h-full object-cover', locked && 'opacity-60')} />
        {locked && (
          <div className="absolute inset-0 flex items-center justify-center bg-black/40">
            <Lock size={16} className="text-white" />
          </div>
        )}
      </div>

      <div className="flex flex-col flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="text-[10px] font-bold uppercase tracking-widest text-gray-500 dark:text-gray-300 truncate">{episode.showTitle}</span>
          {episode.isEarlyAccess && locked && (
            <span className="text-[9px] font-bold uppercase tracking-widest text-[#f6c644] bg-[#f6c644]/10 px-1.5 py-0.5 rounded">Early access</span>
          )}
          {episode.isPremium && !episode.isEarlyAccess && !episode.isOwned && (
            <span className="text-[9px] font-bold uppercase tracking-widest text-[#f6c644] bg-[#f6c644]/10 px-1.5 py-0.5 rounded">Premium</span>
          )}
          {episode.isOwned && (
            <span className="flex items-center gap-1 text-[9px] font-bold uppercase tracking-widest text-beatz-green bg-beatz-green/10 px-1.5 py-0.5 rounded">
              <Check size={9} strokeWidth={3} /> Owned
            </span>
          )}
        </div>
        <span className={cn('font-bold truncate', isCurrent ? 'text-beatz-green' : 'text-beatz-dark-bg dark:text-white')}>
          {episode.title}
        </span>
        <span className="text-sm text-gray-500 dark:text-gray-300 truncate hidden sm:block">
          {episode.isEarlyAccess && locked && episode.publicAt
            ? `Free for everyone on ${relativeDate(episode.publicAt)} · or unlock early`
            : episode.description}
        </span>
      </div>

      {locked ? (
        <button
          onClick={() => onBuy?.(episode)}
          className="shrink-0 flex items-center gap-2 h-9 px-4 rounded-full bg-[#f6c644] text-black text-xs font-bold hover:scale-105 transition-transform"
        >
          <Lock size={14} /> {episode.isEarlyAccess ? 'Unlock' : 'Buy'} {episode.price ? formatPrice(episode.price) : ''}
        </button>
      ) : (
        <button
          onClick={onPlay}
          aria-label={isPlaying ? 'Pause episode' : 'Play episode'}
          className="shrink-0 flex items-center gap-2 h-9 pl-2.5 pr-4 rounded-full border border-gray-300 dark:border-white/15 text-beatz-dark-bg dark:text-white text-xs font-bold hover:border-beatz-green hover:text-beatz-green transition-colors"
        >
          {isPlaying ? <Pause size={16} fill="currentColor" /> : <Play size={16} fill="currentColor" />}
          {formatTotalDuration(episode.duration)}
        </button>
      )}

      <div className="hidden md:flex items-center gap-3 text-gray-400 shrink-0">
        <span className="text-xs font-mono w-12 text-right">{relativeDate(episode.publishedAt)}</span>
        <button className="hover:text-beatz-dark-bg dark:hover:text-white transition-colors opacity-0 group-hover:opacity-100" aria-label="Save episode"><Plus size={18} /></button>
        <button className="hover:text-beatz-dark-bg dark:hover:text-white transition-colors opacity-0 group-hover:opacity-100" aria-label="Share episode"><Share2 size={16} /></button>
      </div>
    </div>
  )
}
