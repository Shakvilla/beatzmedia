import { createFileRoute, getRouteApi } from '@tanstack/react-router'
import { useSuspenseQuery } from '@tanstack/react-query'
import { CatalogGrid } from '../features/store/components/catalog-grid'
import { StoreTabHeading } from '../features/store/components/store-tab-heading'
import { useStoreCart } from '../features/store/use-store-cart'
import { filterByQuery } from '../features/store/filter-by-query'
import { storeListQuery } from '../lib/api/queries/store'

// `StoreResource.type` is single-valued on the backend (exact enum-name match,
// no CSV support), so "Hi-Fi" — which spans TRACK + ALBUM — can't be expressed
// as one `type` query param. We omit `type` (fetch the whole catalog page) and
// filter to TRACK/ALBUM client-side instead.
export const Route = createFileRoute('/store/hifi')({
  loader: ({ context: { queryClient } }) => queryClient.ensureQueryData(storeListQuery({})),
  component: HifiTab,
})

const storeApi = getRouteApi('/store')

function HifiTab() {
  const { q, sort } = storeApi.useSearch()
  const { addToCart } = useStoreCart()
  const { data: allItems } = useSuspenseQuery(storeListQuery({ sort }))
  const items = filterByQuery(
    allItems.filter((item) => item.type === 'TRACK' || item.type === 'ALBUM'),
    q,
  )

  return (
    <div className="flex flex-col gap-8">
      <StoreTabHeading title="Hi-Fi lossless" subtitle="Studio-grade masters, downloaded and owned forever" count={items.length} />
      <CatalogGrid items={items} onAdd={addToCart} emptyHint="No lossless releases match your search." />
    </div>
  )
}
