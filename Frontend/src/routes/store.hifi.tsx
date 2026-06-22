import { createFileRoute, getRouteApi } from '@tanstack/react-router'
import { CatalogGrid } from '../features/store/components/catalog-grid'
import { StoreTabHeading } from '../features/store/components/store-tab-heading'
import { useStoreCart } from '../features/store/use-store-cart'
import { hifiItems, filterStoreItems } from '../lib/store-data'

export const Route = createFileRoute('/store/hifi')({
  component: HifiTab,
})

const storeApi = getRouteApi('/store')

function HifiTab() {
  const { q, sort } = storeApi.useSearch()
  const { addToCart } = useStoreCart()
  const items = filterStoreItems(hifiItems, { q, sort })

  return (
    <div className="flex flex-col gap-8">
      <StoreTabHeading title="Hi-Fi lossless" subtitle="Studio-grade masters, downloaded and owned forever" count={items.length} />
      <CatalogGrid items={items} onAdd={addToCart} emptyHint="No lossless releases match your search." />
    </div>
  )
}
