import { createFileRoute, getRouteApi } from '@tanstack/react-router'
import { CatalogGrid } from '../features/store/components/catalog-grid'
import { StoreTabHeading } from '../features/store/components/store-tab-heading'
import { useStoreCart } from '../features/store/use-store-cart'
import { merchItems, filterStoreItems } from '../lib/store-data'

export const Route = createFileRoute('/store/merch')({
  component: MerchTab,
})

const storeApi = getRouteApi('/store')

function MerchTab() {
  const { q, sort } = storeApi.useSearch()
  const { addToCart } = useStoreCart()
  const items = filterStoreItems(merchItems, { q, sort })

  return (
    <div className="flex flex-col gap-8">
      <StoreTabHeading title="Official merch" subtitle="Apparel & vinyl shipped across Ghana" count={items.length} />
      <CatalogGrid items={items} onAdd={addToCart} emptyHint="No merch matches your search." />
    </div>
  )
}
