import { useEffect, useState } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { cn } from '../../utils/cn'

export interface Paged<T> {
  page: number
  setPage: (p: number) => void
  pageCount: number
  pageItems: T[]
  total: number
  size: number
}

/** Slices a list into pages; resets to page 1 when the result set size changes. */
export function usePaged<T>(items: T[], size = 8): Paged<T> {
  const [page, setPage] = useState(1)
  const pageCount = Math.max(1, Math.ceil(items.length / size))
  useEffect(() => { setPage(1) }, [items.length])
  const safePage = Math.min(page, pageCount)
  const start = (safePage - 1) * size
  return { page: safePage, setPage, pageCount, pageItems: items.slice(start, start + size), total: items.length, size }
}

/**
 * The server-driven twin of {@link usePaged}: the endpoint already sliced the page, so `items` IS
 * the page and `pageCount` comes from the server's `total`. Returns the same {@link Paged} shape,
 * so `Pagination` renders it without any change. The caller owns `page` state (and resets it to 1
 * when its filters change).
 */
export function useServerPaged<T>({
  items,
  total,
  page,
  setPage,
  size,
}: {
  items: T[]
  total: number
  page: number
  setPage: (p: number) => void
  size: number
}): Paged<T> {
  const pageCount = Math.max(1, Math.ceil(total / size))
  return { page, setPage, pageCount, pageItems: items, total, size }
}

/** At most this many numbered page buttons render at once; beyond that the range windows around the current page. */
const MAX_NUMBERED_PAGES = 7

/** Windows the numbered-button range: identical `[1..pageCount]` when small, else 7 centred on `page` and clamped to the ends. */
function pageWindow(page: number, pageCount: number): number[] {
  if (pageCount <= MAX_NUMBERED_PAGES) return Array.from({ length: pageCount }, (_, i) => i + 1)
  let start = page - Math.floor(MAX_NUMBERED_PAGES / 2)
  start = Math.max(1, Math.min(start, pageCount - MAX_NUMBERED_PAGES + 1))
  return Array.from({ length: MAX_NUMBERED_PAGES }, (_, i) => start + i)
}

export function Pagination<T>({ paged }: { paged: Paged<T> }) {
  const { page, pageCount, setPage, total, size } = paged
  if (pageCount <= 1) return null
  const from = (page - 1) * size + 1
  const to = Math.min(page * size, total)

  return (
    <div className="flex items-center justify-between gap-4 pt-3 mt-1 border-t border-gray-200 dark:border-white/10">
      <span className="text-xs text-gray-500 dark:text-gray-400">{from}–{to} of {total}</span>
      <div className="flex items-center gap-1">
        <button onClick={() => setPage(page - 1)} disabled={page === 1} aria-label="Previous page"
          className="w-8 h-8 flex items-center justify-center rounded-lg text-gray-500 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-white/10 disabled:opacity-30 disabled:hover:bg-transparent transition-colors">
          <ChevronLeft size={16} />
        </button>
        {pageWindow(page, pageCount).map((n) => (
          <button key={n} onClick={() => setPage(n)}
            className={cn('w-8 h-8 rounded-lg text-sm font-bold transition-colors',
              n === page ? 'bg-beatz-green/15 text-beatz-green' : 'text-gray-500 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-white/10')}>
            {n}
          </button>
        ))}
        <button onClick={() => setPage(page + 1)} disabled={page === pageCount} aria-label="Next page"
          className="w-8 h-8 flex items-center justify-center rounded-lg text-gray-500 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-white/10 disabled:opacity-30 disabled:hover:bg-transparent transition-colors">
          <ChevronRight size={16} />
        </button>
      </div>
    </div>
  )
}
