import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as client from '../client'
import { eventsListQuery, eventQuery } from './events'

vi.mock('../client')

const wire = {
  id: 'iron-boy-live',
  title: 'Iron Boy Live',
  artistId: 'black-sherif',
  artistName: 'Black Sherif',
  lineup: null,
  image: 'x',
  date: '2026-07-09T19:00:00Z',
  doorsTime: '7:00 PM',
  venue: 'Independence Square, Accra',
  city: 'Accra',
  region: null,
  status: 'selling-fast',
  category: 'Concert',
  description: null,
  ticketTiers: [
    { name: 'Regular', price: { amount: 150, currency: 'GHS' }, perks: [], soldOut: false },
  ],
  popularity: null,
  ageRestriction: null,
}

beforeEach(() => vi.resetAllMocks())

describe('events queries', () => {
  it('lists events and unwraps the page', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue({ items: [wire], page: 1, size: 20, total: 1 })
    const evs = await eventsListQuery().queryFn!({} as never)
    expect(client.apiFetch).toHaveBeenCalledWith('/events?page=1&size=48')
    expect(evs[0].id).toBe('iron-boy-live')
    expect(evs[0].ticketTiers[0].price).toEqual({ amount: 150, currency: 'GHS' })
  })

  it('passes city and category filters', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue({ items: [], page: 1, size: 20, total: 0 })
    await eventsListQuery({ city: 'Accra', category: 'Concert' }).queryFn!({} as never)
    expect(client.apiFetch).toHaveBeenCalledWith('/events?city=Accra&category=Concert&page=1&size=48')
  })

  it('fetches one event by id', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue(wire)
    const ev = await eventQuery('iron-boy-live').queryFn!({} as never)
    expect(client.apiFetch).toHaveBeenCalledWith('/events/iron-boy-live')
    expect(ev.ticketTiers[0].price).toEqual({ amount: 150, currency: 'GHS' })
  })
})
