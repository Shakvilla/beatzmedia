import type { ReactNode } from 'react'

interface MediaRailProps {
  title: string
  subtitle?: string
  /** Optional right-aligned action, e.g. a "See all" link. */
  action?: ReactNode
  children: ReactNode
}

/**
 * Editorial horizontal carousel used across Discover (Apple Music / Tidal style).
 * Children should be fixed-width so cards peek and scroll with snap; the row
 * bleeds to the page padding edges.
 */
export function MediaRail({ title, subtitle, action, children }: MediaRailProps) {
  return (
    <section className="flex flex-col gap-5">
      <div className="flex items-end justify-between gap-4 border-b border-gray-200 dark:border-white/5 pb-2">
        <div className="flex flex-col gap-1">
          <h2 className="text-title text-beatz-dark-bg dark:text-white">{title}</h2>
          {subtitle && <p className="text-sm text-gray-500 dark:text-gray-300">{subtitle}</p>}
        </div>
        {action}
      </div>
      <div className="flex gap-5 overflow-x-auto no-scrollbar snap-x pb-2 -mx-4 md:-mx-8 px-4 md:px-8">{children}</div>
    </section>
  )
}
