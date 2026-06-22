import { createFileRoute, getRouteApi } from '@tanstack/react-router'
import { CatalogGrid } from '../features/store/components/catalog-grid'
import { StoreTabHeading } from '../features/store/components/store-tab-heading'
import { useStoreCart } from '../features/store/use-store-cart'
import { beatItems, filterStoreItems } from '../lib/store-data'
import type { LicenseTier } from '../types'
import { cn } from '../utils/cn'

export const Route = createFileRoute('/store/beats')({
  component: BeatsTab,
})

const storeApi = getRouteApi('/store')

const TIER_FILTERS: { value: LicenseTier | undefined; label: string }[] = [
  { value: undefined, label: 'All licenses' },
  { value: 'LEASE', label: 'Basic Lease' },
  { value: 'PREMIUM', label: 'Premium Stems' },
  { value: 'EXCLUSIVE', label: 'Exclusive' },
]

function BeatsTab() {
  const { q, tier, sort } = storeApi.useSearch()
  const navigate = storeApi.useNavigate()
  const { addToCart } = useStoreCart()
  const items = filterStoreItems(beatItems, { q, tier, sort })

  return (
    <div className="flex flex-col gap-8">
      <StoreTabHeading title="Beats & stems" subtitle="License instrumentals for your next record" count={items.length} />
      <div className="flex items-center gap-2 flex-wrap">
        {TIER_FILTERS.map((filter) => {
          const active = tier === filter.value
          return (
            <button
              key={filter.label}
              onClick={() => navigate({ search: (prev) => ({ ...prev, tier: filter.value }) })}
              className={cn(
                'px-4 py-2 rounded-full text-sm font-bold transition-colors',
                active
                  ? 'bg-beatz-dark-bg dark:bg-white text-white dark:text-black'
                  : 'bg-white dark:bg-beatz-dark-surface-2 text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-beatz-dark-surface-3',
              )}
            >
              {filter.label}
            </button>
          )
        })}
      </div>

      <CatalogGrid items={items} onAdd={addToCart} emptyHint="No beats match these license filters." />
    </div>
  )
}
