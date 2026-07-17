import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import {
  createRootRoute,
  createRoute,
  createRouter,
  createMemoryHistory,
  RouterProvider,
} from '@tanstack/react-router'
import { EventCard } from './event-card'
import type { Event } from '../../../types'

const event: Event = {
  id: 'iron-boy-live',
  title: 'Iron Boy Live',
  artistName: 'Black Sherif',
  artistId: 'black-sherif',
  image: 'x',
  date: '2026-07-09T19:00:00Z',
  venue: 'Independence Square',
  city: 'Accra',
  status: 'selling-fast',
  category: 'Concert',
  ticketTiers: [{ name: 'Regular', price: { amount: 150, currency: 'GHS' }, perks: [] }],
}

function renderWithRouter(ui: React.ReactElement) {
  const rootRoute = createRootRoute({ component: () => ui })
  const eventRoute = createRoute({ getParentRoute: () => rootRoute, path: '/event/$eventId' })
  const routeTree = rootRoute.addChildren([eventRoute])
  const router = createRouter({ routeTree, history: createMemoryHistory({ initialEntries: ['/'] }) })
  return render(<RouterProvider router={router} />)
}

describe('EventCard', () => {
  it('renders the passed event without touching event-data', async () => {
    renderWithRouter(<EventCard event={event} />)
    expect(await screen.findByText('Iron Boy Live')).toBeTruthy()
  })
})
