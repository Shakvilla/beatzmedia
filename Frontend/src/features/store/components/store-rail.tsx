import { Link } from '@tanstack/react-router'
import { ChevronRight } from 'lucide-react'
import type { StoreItem } from '../../../types'
import { PremiumProductCard } from './premium-product-card'

export type StoreTabPath = '/store/hifi' | '/store/beats' | '/store/merch' | '/store/exclusives'

interface StoreRailProps {
  title: string
  subtitle?: string
  viewAllTo: StoreTabPath
  items: StoreItem[]
  onAdd: (item: StoreItem) => void
}

/**
 * Editorial horizontal carousel (Apple Music / Tidal style): cards are a fixed
 * width and scroll with snap, bleeding to the page padding edges.
 */
export function StoreRail({ title, subtitle, viewAllTo, items, onAdd }: StoreRailProps) {
  return (
    <section className="flex flex-col gap-5">
      <div className="flex items-end justify-between gap-4">
        <div className="flex flex-col gap-1">
          <Link to={viewAllTo} className="group inline-flex items-center gap-1 w-fit">
            <h2 className="text-title text-beatz-dark-bg dark:text-white group-hover:text-beatz-green transition-colors">{title}</h2>
            <ChevronRight size={22} className="text-gray-400 group-hover:text-beatz-green group-hover:translate-x-0.5 transition-all" />
          </Link>
          {subtitle && <p className="text-sm text-gray-500 dark:text-gray-300">{subtitle}</p>}
        </div>
        <Link
          to={viewAllTo}
          className="shrink-0 text-xs font-mono uppercase tracking-widest text-gray-500 dark:text-gray-300 hover:text-beatz-green transition-colors"
        >
          See all
        </Link>
      </div>

      <div className="flex gap-5 overflow-x-auto no-scrollbar snap-x pb-2 -mx-4 md:-mx-8 px-4 md:px-8">
        {items.map((item) => (
          <div key={item.id} className="snap-start shrink-0 w-44 sm:w-48 lg:w-52">
            <PremiumProductCard item={item} onAdd={onAdd} />
          </div>
        ))}
      </div>
    </section>
  )
}
