import { Link } from '@tanstack/react-router'
import type { ReactNode } from 'react'
import type { FollowingFeedItem } from '../../lib/api/queries/following'

export function FollowingItemLink({ item, children, className }: {
  item: FollowingFeedItem
  children: ReactNode
  className?: string
}) {
  if (item.kind === 'release') {
    return <Link to="/album/$albumId" params={{ albumId: item.contentId }} className={className}>{children}</Link>
  }
  return <Link to="/podcast/$podcastId" params={{ podcastId: item.creatorId }} className={className}>{children}</Link>
}
