import { createFileRoute, getRouteApi } from '@tanstack/react-router'
import { useSuspenseQuery } from '@tanstack/react-query'
import { CatalogGrid } from '../features/store/components/catalog-grid'
import { StoreTabHeading } from '../features/store/components/store-tab-heading'
import { useStoreCart } from '../features/store/use-store-cart'
import { filterByQuery } from '../features/store/filter-by-query'
import { storeListQuery } from '../lib/api/queries/store'

export const Route = createFileRoute('/store/exclusives')({
  loader: ({ context: { queryClient } }) =>
    queryClient.ensureQueryData(storeListQuery({ type: 'EXCLUSIVE' })),
  component: ExclusivesTab,
})

const storeApi = getRouteApi('/store')

function ExclusivesTab() {
  const { q, sort } = storeApi.useSearch()
  const { addToCart } = useStoreCart()
  const { data: allItems } = useSuspenseQuery(storeListQuery({ type: 'EXCLUSIVE', sort }))
  const items = filterByQuery(allItems, q)

  return (
    <div className="flex flex-col gap-8">
      <StoreTabHeading title="Exclusive drops" subtitle="Limited VIP experiences and early access" count={items.length} />
      <CatalogGrid items={items} onAdd={addToCart} emptyHint="No exclusives match your search." />
    </div>
  )
}
