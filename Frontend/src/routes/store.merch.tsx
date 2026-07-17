import { createFileRoute, getRouteApi } from '@tanstack/react-router'
import { useSuspenseQuery } from '@tanstack/react-query'
import { CatalogGrid } from '../features/store/components/catalog-grid'
import { StoreTabHeading } from '../features/store/components/store-tab-heading'
import { useStoreCart } from '../features/store/use-store-cart'
import { filterByQuery } from '../features/store/filter-by-query'
import { storeListQuery } from '../lib/api/queries/store'

export const Route = createFileRoute('/store/merch')({
  loader: ({ context: { queryClient } }) =>
    queryClient.ensureQueryData(storeListQuery({ type: 'MERCH' })),
  component: MerchTab,
})

const storeApi = getRouteApi('/store')

function MerchTab() {
  const { q, sort } = storeApi.useSearch()
  const { addToCart } = useStoreCart()
  const { data: allItems } = useSuspenseQuery(storeListQuery({ type: 'MERCH', sort }))
  // The backend `/v1/store` endpoint has no free-text search param, so the
  // header search box filters client-side over the already-fetched page.
  const items = filterByQuery(allItems, q)

  return (
    <div className="flex flex-col gap-8">
      <StoreTabHeading title="Official merch" subtitle="Apparel & vinyl shipped across Ghana" count={items.length} />
      <CatalogGrid items={items} onAdd={addToCart} emptyHint="No merch matches your search." />
    </div>
  )
}
