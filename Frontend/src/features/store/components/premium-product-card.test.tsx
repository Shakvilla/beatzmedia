import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import {
  createRootRoute,
  createRoute,
  createRouter,
  createMemoryHistory,
  RouterProvider,
} from '@tanstack/react-router'
import { PremiumProductCard } from './premium-product-card'
import type { StoreItem } from '../../../types'

const item: StoreItem = {
  id: 'merch-bsherif-tee', type: 'MERCH', title: 'Iron Boy Tour Tee',
  artistName: 'Black Sherif', artistId: 'black-sherif', image: 'x', price: { amount: 120, currency: 'GHS' },
} as StoreItem

function renderWithRouter(ui: React.ReactElement) {
  const rootRoute = createRootRoute({ component: () => ui })
  const itemRoute = createRoute({ getParentRoute: () => rootRoute, path: '/store/$itemId' })
  const routeTree = rootRoute.addChildren([itemRoute])
  const router = createRouter({ routeTree, history: createMemoryHistory({ initialEntries: ['/'] }) })
  return render(<RouterProvider router={router} />)
}

describe('PremiumProductCard', () => {
  it('renders the passed item without touching store-data', async () => {
    renderWithRouter(<PremiumProductCard item={item} />)
    expect(await screen.findByText('Iron Boy Tour Tee')).toBeTruthy()
  })
})
