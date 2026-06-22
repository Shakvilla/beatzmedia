import { useEffect, useState } from 'react'
import { Link } from '@tanstack/react-router'
import { Play, Sparkles, ChevronLeft, ChevronRight, ArrowRight } from 'lucide-react'
import type { Album } from '../../../types'
import { usePlayer } from '../../player/player-context'
import { getAlbumTracks } from '../../../lib/mock-data'
import { cn } from '../../../utils/cn'

const INTERVAL = 6000

/**
 * Featured-albums carousel — the premium "promoted" placement at the top of
 * the home page. Artists pay to have an album featured here. Auto-advances,
 * pauses on hover, with arrows + dot indicators.
 */
export function FeaturedCarousel({ albums }: { albums: Album[] }) {
  const { playQueue } = usePlayer()
  const [index, setIndex] = useState(0)
  const [paused, setPaused] = useState(false)
  const count = albums.length

  useEffect(() => {
    if (paused || count <= 1) return
    const id = window.setInterval(() => setIndex((i) => (i + 1) % count), INTERVAL)
    return () => window.clearInterval(id)
  }, [paused, count])

  if (count === 0) return null
  const go = (i: number) => setIndex(((i % count) + count) % count)

  return (
    <div
      className="relative overflow-hidden rounded-3xl group min-h-[300px] sm:min-h-[360px] lg:min-h-[420px]"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
    >
      <div className="flex h-full transition-transform duration-500 ease-out" style={{ transform: `translateX(-${index * 100}%)` }}>
        {albums.map((album) => (
          <Link
            key={album.id}
            to="/album/$albumId"
            params={{ albumId: album.id }}
            className="relative w-full shrink-0 min-h-[300px] sm:min-h-[360px] lg:min-h-[420px] flex"
          >
            <img src={album.coverImage} alt={album.title} className="absolute inset-0 w-full h-full object-cover" />
            <div className="absolute inset-0 bg-gradient-to-r from-black/90 via-black/60 to-transparent" />
            <div className="relative z-10 flex flex-col justify-end gap-4 p-6 sm:p-10 lg:p-14 max-w-2xl">
              <div className="flex items-center gap-3">
                <span className="flex items-center gap-2 text-xs font-bold tracking-[0.3em] uppercase text-[#f6c644]">
                  <Sparkles size={14} /> Featured Album
                </span>
                <span className="text-[9px] font-bold uppercase tracking-widest text-white/60 border border-white/20 px-1.5 py-0.5 rounded">Promoted</span>
              </div>
              <h2 className="text-3xl sm:text-4xl lg:text-6xl font-bold text-white tracking-tight leading-[1.05] break-words">{album.title}</h2>
              <p className="text-base sm:text-lg text-gray-200">
                {album.artistName} • {album.year} • {album.trackIds.length} tracks
              </p>
              <div className="flex items-center gap-5 mt-2">
                <button
                  onClick={(e) => {
                    e.preventDefault()
                    e.stopPropagation()
                    playQueue(getAlbumTracks(album.id))
                  }}
                  className="h-12 px-7 rounded-full bg-beatz-green text-black font-bold flex items-center gap-2 hover:scale-105 transition-transform"
                >
                  <Play size={20} fill="currentColor" /> Play album
                </button>
                <span className="text-sm text-white/70 font-bold uppercase tracking-widest flex items-center gap-1">View album <ArrowRight size={14} /></span>
              </div>
            </div>
          </Link>
        ))}
      </div>

      {count > 1 && (
        <>
          <button
            onClick={() => go(index - 1)}
            aria-label="Previous"
            className="absolute left-4 top-1/2 -translate-y-1/2 z-20 w-10 h-10 rounded-full bg-black/40 backdrop-blur text-white flex items-center justify-center opacity-100 md:opacity-0 md:group-hover:opacity-100 hover:bg-black/60 transition-all"
          >
            <ChevronLeft size={22} />
          </button>
          <button
            onClick={() => go(index + 1)}
            aria-label="Next"
            className="absolute right-4 top-1/2 -translate-y-1/2 z-20 w-10 h-10 rounded-full bg-black/40 backdrop-blur text-white flex items-center justify-center opacity-100 md:opacity-0 md:group-hover:opacity-100 hover:bg-black/60 transition-all"
          >
            <ChevronRight size={22} />
          </button>

          <div className="absolute bottom-6 right-8 flex gap-2 z-20">
            {albums.map((album, i) => (
              <button
                key={album.id}
                onClick={() => setIndex(i)}
                aria-label={`Go to slide ${i + 1}`}
                className={cn('h-1.5 rounded-full transition-all', i === index ? 'w-6 bg-beatz-green' : 'w-1.5 bg-white/40 hover:bg-white/70')}
              />
            ))}
          </div>
        </>
      )}
    </div>
  )
}
