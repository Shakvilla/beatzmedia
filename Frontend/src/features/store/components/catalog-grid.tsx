import { Link } from '@tanstack/react-router'
import { PackageOpen } from 'lucide-react'
import type { StoreItem } from '../../../types'
import { PremiumProductCard } from './premium-product-card'

interface CatalogGridProps {
  items: StoreItem[]
  onAdd: (item: StoreItem) => void
  /** Shown when there are no results. */
  emptyHint?: string
}

export function CatalogGrid({ items, onAdd, emptyHint }: CatalogGridProps) {
  if (items.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center text-center gap-4 py-20 rounded-2xl border border-dashed border-gray-200 dark:border-white/10">
        <div className="w-14 h-14 rounded-full bg-gray-100 dark:bg-white/5 flex items-center justify-center">
          <PackageOpen className="text-gray-400 dark:text-gray-500" size={26} />
        </div>
        <div className="flex flex-col gap-1">
          <span className="font-bold text-beatz-dark-bg dark:text-white">No items match your filters</span>
          <span className="text-sm text-gray-500 dark:text-gray-300">{emptyHint ?? 'Try a different search or sort order.'}</span>
        </div>
        <Link
          to="/store"
          search={{}}
          className="mt-2 h-10 px-5 rounded-full bg-white dark:bg-beatz-dark-surface-2 border border-gray-200 dark:border-white/15 text-sm font-bold text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-beatz-dark-surface-3 transition-colors"
        >
          Reset filters
        </Link>
      </div>
    )
  }

  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-x-6 gap-y-10">
      {items.map((item) => (
        <PremiumProductCard key={item.id} item={item} onAdd={onAdd} />
      ))}
    </div>
  )
}
