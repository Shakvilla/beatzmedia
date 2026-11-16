import { Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { ArrowRight, Disc3, Mic2 } from 'lucide-react'
import { useAuth } from '../auth/auth-context'
import { MediaRail } from '../discover/components/media-rail'
import { followingFeedQuery } from '../../lib/api/queries/following'
import { FollowingItemLink } from './following-item-link'

const RAIL_ITEM = 'snap-start shrink-0 w-44 sm:w-48 lg:w-52'

export function FollowingFeedPreview() {
  const { isAuthenticated } = useAuth()
  const query = useQuery(followingFeedQuery(isAuthenticated))
  if (!isAuthenticated || query.isError || !query.data?.items.length) return null

  return (
    <MediaRail
      title="From artists you follow"
      subtitle="New music and episodes, newest first"
      action={(
        <Link to="/following" className="flex items-center gap-1 text-xs font-bold uppercase tracking-widest text-beatz-green hover:underline">
          View all <ArrowRight size={13} />
        </Link>
      )}
    >
      {query.data.items.map((item) => (
        <div key={`${item.kind}:${item.contentId}`} className={RAIL_ITEM}>
          <FollowingItemLink item={item} className="group block">
            <div className="relative aspect-square overflow-hidden rounded-2xl bg-beatz-light-surface-2 shadow-lg dark:bg-beatz-dark-surface-3">
              {item.image && <img src={item.image} alt="" className="h-full w-full object-cover transition duration-500 group-hover:scale-105" />}
              <span className="absolute left-3 top-3 flex items-center gap-1.5 rounded-full bg-black/70 px-2.5 py-1 text-[10px] font-bold uppercase tracking-widest text-white backdrop-blur">
                {item.kind === 'release' ? <Disc3 size={11} /> : <Mic2 size={11} />}
                {item.kind === 'release' ? 'New release' : 'New episode'}
              </span>
            </div>
            <h3 className="mt-3 truncate font-bold text-beatz-dark-bg dark:text-white">{item.title}</h3>
            <p className="mt-1 truncate text-sm text-gray-500 dark:text-gray-300">{item.subtitle}</p>
          </FollowingItemLink>
        </div>
      ))}
    </MediaRail>
  )
}
