import { Link } from '@tanstack/react-router'
import { BadgeCheck } from 'lucide-react'
import type { Artist } from '../../../types'
import { formatCount } from '../../../lib/format'

export function ArtistCircle({ artist }: { artist: Artist }) {
  return (
    <Link
      to="/artist/$artistId"
      params={{ artistId: artist.id }}
      className="group flex flex-col items-center gap-3 text-center"
    >
      <div className="w-full aspect-square rounded-full overflow-hidden ring-1 ring-black/5 dark:ring-white/5 shadow-sm group-hover:shadow-xl transition-all duration-300">
        <img
          src={artist.image}
          alt={artist.name}
          className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"
        />
      </div>
      <div className="flex flex-col items-center">
        <span className="flex items-center gap-1 font-bold text-[15px] text-beatz-dark-bg dark:text-white truncate max-w-full group-hover:text-beatz-green transition-colors">
          <span className="truncate">{artist.name}</span>
          {artist.verified && <BadgeCheck size={14} className="text-beatz-blue shrink-0" />}
        </span>
        <span className="text-xs text-gray-500 dark:text-gray-300">{formatCount(artist.monthlyListeners)} monthly</span>
      </div>
    </Link>
  )
}
