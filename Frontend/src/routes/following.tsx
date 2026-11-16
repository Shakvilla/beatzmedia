/* eslint-disable react-refresh/only-export-components -- TanStack file routes export Route beside their component. */
import { createFileRoute, Link } from '@tanstack/react-router'
import { useInfiniteQuery } from '@tanstack/react-query'
import { ArrowDown, Disc3, Mic2, RadioTower, UsersRound } from 'lucide-react'
import { useAuth } from '../features/auth/auth-context'
import { FollowingItemLink } from '../features/discovery/following-item-link'
import { apiFollowingFeed, type FollowingFeedItem } from '../lib/api/queries/following'

export const Route = createFileRoute('/following')({ component: FollowingPage })

function dayLabel(iso: string): string {
  const value = new Date(iso)
  const today = new Date()
  if (value.toDateString() === today.toDateString()) return 'Today'
  const yesterday = new Date(today)
  yesterday.setDate(today.getDate() - 1)
  if (value.toDateString() === yesterday.toDateString()) return 'Yesterday'
  return value.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' })
}

function groupByDay(items: FollowingFeedItem[]) {
  const groups = new Map<string, FollowingFeedItem[]>()
  for (const item of items) {
    const label = dayLabel(item.publishedAt)
    groups.set(label, [...(groups.get(label) ?? []), item])
  }
  return [...groups.entries()]
}

function FollowingPage() {
  const { isAuthenticated, isLoading } = useAuth()
  const feed = useInfiniteQuery({
    queryKey: ['me', 'following', 'infinite'],
    queryFn: ({ pageParam }) => apiFollowingFeed(pageParam, 20),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    enabled: isAuthenticated,
  })

  if (isLoading) return <FollowingSkeleton />
  if (!isAuthenticated) return <SignedOutFollowing />

  const items = feed.data?.pages.flatMap((page) => page.items) ?? []
  if (!feed.isPending && items.length === 0) return <EmptyFollowing />

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-10 pb-20">
      <header className="relative overflow-hidden rounded-[2rem] border border-gray-200 bg-white px-7 py-10 shadow-sm dark:border-white/10 dark:bg-beatz-dark-surface sm:px-10">
        <div className="absolute -right-12 -top-16 h-52 w-52 rounded-full bg-beatz-green/15 blur-3xl" />
        <div className="relative flex max-w-2xl flex-col gap-4">
          <span className="flex items-center gap-2 text-xs font-bold uppercase tracking-[0.25em] text-beatz-green">
            <RadioTower size={15} /> Your signal
          </span>
          <h1 className="text-display tracking-tight text-beatz-dark-bg dark:text-white">Following</h1>
          <p className="max-w-xl text-gray-600 dark:text-gray-300">
            A clean timeline of new music and podcast episodes from creators you chose—nothing paid, nothing guessed.
          </p>
        </div>
      </header>

      {feed.isPending ? <FollowingSkeleton /> : (
        <div className="flex flex-col gap-12">
          {groupByDay(items).map(([day, entries]) => (
            <section key={day} className="grid gap-5 md:grid-cols-[9rem_1fr]">
              <h2 className="pt-2 text-xs font-bold uppercase tracking-[0.2em] text-gray-500 dark:text-gray-400">{day}</h2>
              <div className="flex flex-col gap-3">
                {entries.map((item) => <TimelineItem key={`${item.kind}:${item.contentId}`} item={item} />)}
              </div>
            </section>
          ))}
        </div>
      )}

      {feed.hasNextPage && (
        <button
          type="button"
          onClick={() => feed.fetchNextPage()}
          disabled={feed.isFetchingNextPage}
          className="mx-auto flex h-11 items-center gap-2 rounded-full border border-gray-300 px-6 text-sm font-bold text-beatz-dark-bg transition hover:border-beatz-green hover:text-beatz-green disabled:opacity-50 dark:border-white/15 dark:text-white"
        >
          <ArrowDown size={15} /> {feed.isFetchingNextPage ? 'Loading…' : 'Load earlier updates'}
        </button>
      )}
    </div>
  )
}

function TimelineItem({ item }: { item: FollowingFeedItem }) {
  const isRelease = item.kind === 'release'
  return (
    <FollowingItemLink
      item={item}
      className="group grid grid-cols-[5.5rem_1fr_auto] items-center gap-4 rounded-2xl border border-gray-200 bg-white p-3 shadow-sm transition hover:-translate-y-0.5 hover:border-beatz-green/50 hover:shadow-lg dark:border-white/5 dark:bg-beatz-dark-surface"
    >
      <div className="aspect-square overflow-hidden rounded-xl bg-beatz-light-surface-2 dark:bg-beatz-dark-surface-3">
        {item.image && <img src={item.image} alt="" className="h-full w-full object-cover transition duration-500 group-hover:scale-105" />}
      </div>
      <div className="min-w-0">
        <span className="mb-1 flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-[0.18em] text-beatz-green">
          {isRelease ? <Disc3 size={12} /> : <Mic2 size={12} />}
          {isRelease ? 'New release' : 'New episode'}
        </span>
        <h3 className="truncate text-lg font-bold text-beatz-dark-bg dark:text-white">{item.title}</h3>
        <p className="truncate text-sm text-gray-500 dark:text-gray-300">{item.subtitle}</p>
      </div>
      <span className="hidden pr-3 text-xs font-bold text-gray-400 transition group-hover:text-beatz-green sm:block">Open →</span>
    </FollowingItemLink>
  )
}

function SignedOutFollowing() {
  return (
    <div className="mx-auto flex max-w-xl flex-col items-center gap-5 py-28 text-center">
      <div className="flex h-16 w-16 items-center justify-center rounded-full bg-beatz-green/10 text-beatz-green"><UsersRound size={28} /></div>
      <h1 className="text-title text-beatz-dark-bg dark:text-white">Your Following feed travels with you</h1>
      <p className="text-gray-500 dark:text-gray-300">Sign in to see new releases and episodes from the creators you follow.</p>
      <Link to="/login" className="rounded-full bg-beatz-green px-6 py-3 text-sm font-bold text-black">Sign in</Link>
    </div>
  )
}

function EmptyFollowing() {
  return (
    <div className="mx-auto flex max-w-xl flex-col items-center gap-4 py-24 text-center">
      <RadioTower size={30} className="text-beatz-green" />
      <h1 className="text-title text-beatz-dark-bg dark:text-white">Your signal is quiet—for now</h1>
      <p className="text-gray-500 dark:text-gray-300">Follow artists and podcast shows to build a chronological feed here.</p>
      <div className="flex gap-3">
        <Link to="/search" search={{ q: '' }} className="rounded-full bg-beatz-dark-bg px-5 py-2.5 text-sm font-bold text-white dark:bg-white dark:text-black">Find artists</Link>
        <Link to="/podcasts" className="rounded-full border border-gray-300 px-5 py-2.5 text-sm font-bold text-beatz-dark-bg dark:border-white/15 dark:text-white">Browse shows</Link>
      </div>
    </div>
  )
}

function FollowingSkeleton() {
  return <div className="mx-auto h-80 w-full max-w-5xl animate-pulse rounded-[2rem] bg-gray-100 dark:bg-white/5" aria-label="Loading Following feed" />
}
