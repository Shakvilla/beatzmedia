import { createFileRoute, Outlet, useMatch } from '@tanstack/react-router'
import { z } from 'zod'
import { StoreHeader } from '../features/store/components/store-header'

export const storeSearchSchema = z.object({
  q: z.string().optional().catch(''),
  genre: z.string().optional().catch(''),
  tier: z.enum(['LEASE', 'PREMIUM', 'EXCLUSIVE']).optional().catch(undefined),
  sort: z.enum(['popular', 'newest', 'price-asc', 'price-desc']).optional().catch('popular'),
})

export const Route = createFileRoute('/store')({
  validateSearch: storeSearchSchema,
  component: StoreLayout,
})

function StoreLayout() {
  // The Product Detail Page renders standalone (no tabs / filter bar).
  const isItemRoute = Boolean(useMatch({ from: '/store/$itemId', shouldThrow: false }))

  if (isItemRoute) {
    return <Outlet />
  }

  return (
    <div className="flex flex-col gap-8 pb-12">
      <StoreHeader />
      <Outlet />
    </div>
  )
}
