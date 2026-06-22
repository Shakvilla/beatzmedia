import { createFileRoute, getRouteApi } from '@tanstack/react-router'
import { CatalogGrid } from '../features/store/components/catalog-grid'
import { StoreTabHeading } from '../features/store/components/store-tab-heading'
import { useStoreCart } from '../features/store/use-store-cart'
import { exclusiveItems, filterStoreItems } from '../lib/store-data'

export const Route = createFileRoute('/store/exclusives')({
  component: ExclusivesTab,
})

const storeApi = getRouteApi('/store')

function ExclusivesTab() {
  const { q, sort } = storeApi.useSearch()
  const { addToCart } = useStoreCart()
  const items = filterStoreItems(exclusiveItems, { q, sort })

  return (
    <div className="flex flex-col gap-8">
      <StoreTabHeading title="Exclusive drops" subtitle="Limited VIP experiences and early access" count={items.length} />
      <CatalogGrid items={items} onAdd={addToCart} emptyHint="No exclusives match your search." />
    </div>
  )
}
