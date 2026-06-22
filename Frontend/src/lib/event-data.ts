/**
 * Mock live-events catalog for BeatzClik — Ghanaian concerts, festivals & shows.
 * Ticketing is a live-music revenue stream for artists alongside buy-to-own.
 */

import type { Event } from '../types'

const GHS = (amount: number) => ({ amount, currency: 'GHS' as const })

const IMG = {
  crowd: 'https://images.unsplash.com/photo-1470229722913-7c0e2dbbafd3?q=80&w=1200&auto=format&fit=crop',
  stage: 'https://images.unsplash.com/photo-1501386761578-eac5c94b800a?q=80&w=1200&auto=format&fit=crop',
  festival: 'https://images.unsplash.com/photo-1459749411175-04bf5292ceea?q=80&w=1200&auto=format&fit=crop',
  club: 'https://images.unsplash.com/photo-1566737236500-c8ac43014a67?q=80&w=1200&auto=format&fit=crop',
  jazz: 'https://images.unsplash.com/photo-1415201364774-f6f0bb35f28f?q=80&w=1200&auto=format&fit=crop',
  beach: 'https://images.unsplash.com/photo-1533174072545-7a4b6ad7a6c3?q=80&w=1200&auto=format&fit=crop',
  arena: 'https://images.unsplash.com/photo-1540039155733-5bb30b53aa14?q=80&w=1200&auto=format&fit=crop',
}

export const events: Event[] = [
  {
    id: 'iron-boy-live',
    title: 'Iron Boy Live',
    artistName: 'Black Sherif',
    artistId: 'black-sherif',
    lineup: ['Lasmid', 'Camidoh'],
    image: IMG.stage,
    date: '2026-07-09T19:00:00',
    doorsTime: '7:00 PM',
    venue: 'Independence Square',
    city: 'Accra',
    region: 'Greater Accra',
    status: 'selling-fast',
    category: 'Concert',
    description: 'Black Sherif headlines a homecoming show backed by a full live band, with special guests from across the 233.',
    ageRestriction: 'All ages',
    popularity: 99,
    ticketTiers: [
      { name: 'Regular', price: GHS(150), perks: ['General standing', 'Access from 6 PM'] },
      { name: 'VIP', price: GHS(400), perks: ['Elevated VIP deck', 'Dedicated bar', 'Fast-track entry'] },
      { name: 'VVIP Table (5)', price: GHS(2500), perks: ['Front-stage table for 5', 'Bottle service', 'Backstage tour'] },
    ],
  },
  {
    id: 'outside-tour-accra',
    title: 'Burna Boy — Outside Tour',
    artistName: 'Burna Boy',
    artistId: 'burna-boy',
    lineup: ['Asake'],
    image: IMG.arena,
    date: '2026-08-15T20:00:00',
    doorsTime: '8:00 PM',
    venue: 'Accra Sports Stadium',
    city: 'Accra',
    region: 'Greater Accra',
    status: 'on-sale',
    category: 'Tour',
    description: 'The African Giant brings the Outside Tour to Accra for one massive night.',
    ageRestriction: 'All ages',
    popularity: 96,
    ticketTiers: [
      { name: 'Regular', price: GHS(250) },
      { name: 'VIP', price: GHS(600), perks: ['VIP section', 'Premium viewing'] },
      { name: 'Golden Circle', price: GHS(1200), perks: ['Front pit', 'Exclusive entrance'] },
    ],
  },
  {
    id: 'detty-december',
    title: 'Detty December Festival',
    artistName: 'Multiple Artists',
    lineup: ['Black Sherif', 'King Promise', 'Camidoh', 'Lasmid', 'Sarkodie'],
    image: IMG.festival,
    date: '2026-12-20T16:00:00',
    doorsTime: '4:00 PM',
    venue: 'Labadi Beach',
    city: 'Accra',
    region: 'Greater Accra',
    status: 'on-sale',
    category: 'Festival',
    description: 'Ghana’s biggest end-of-year beach festival — a full day of afrobeats, highlife and amapiano.',
    ageRestriction: '18+',
    popularity: 94,
    ticketTiers: [
      { name: '1-Day Pass', price: GHS(250), perks: ['Single-day entry'] },
      { name: 'Weekend Pass', price: GHS(450), perks: ['Both days', 'Re-entry'] },
      { name: 'VIP Weekend', price: GHS(900), perks: ['VIP zone', 'Free welcome drinks', 'Shaded lounge'] },
    ],
  },
  {
    id: 'five-star-night',
    title: 'King Promise: 5 Star Night',
    artistName: 'King Promise',
    artistId: 'king-promise',
    image: IMG.jazz,
    date: '2026-06-27T21:00:00',
    doorsTime: '9:00 PM',
    venue: '+233 Jazz Bar & Grill',
    city: 'Accra',
    region: 'Greater Accra',
    status: 'selling-fast',
    category: 'Club Night',
    description: 'An intimate late-night set from King Promise in the heart of Accra.',
    ageRestriction: '21+',
    popularity: 88,
    ticketTiers: [
      { name: 'Entry', price: GHS(80) },
      { name: 'Booth (4)', price: GHS(1500), perks: ['Reserved booth for 4', 'Bottle service'] },
    ],
  },
  {
    id: 'asaase-sound-clash',
    title: 'Asaase Sound Clash',
    artistName: 'Multiple Artists',
    lineup: ['Lasmid', 'Camidoh', 'Asake'],
    image: IMG.crowd,
    date: '2026-09-05T18:00:00',
    doorsTime: '6:00 PM',
    venue: 'Baba Yara Stadium',
    city: 'Kumasi',
    region: 'Ashanti',
    status: 'on-sale',
    category: 'Festival',
    description: 'The Garden City’s biggest clash of sounds returns to Baba Yara.',
    ageRestriction: 'All ages',
    popularity: 82,
    ticketTiers: [
      { name: 'Regular', price: GHS(120) },
      { name: 'VIP', price: GHS(350), perks: ['VIP stand', 'Fast-track entry'] },
    ],
  },
  {
    id: 'sugarcane-listening',
    title: 'Sugarcane: Listening Party',
    artistName: 'Camidoh',
    artistId: 'camidoh',
    image: IMG.club,
    date: '2026-06-28T19:30:00',
    doorsTime: '7:30 PM',
    venue: 'Community 1 Arts Centre',
    city: 'Tema',
    region: 'Greater Accra',
    status: 'on-sale',
    category: 'Listening Party',
    description: 'Hear Camidoh’s new project first, with a live Q&A and signing.',
    ageRestriction: 'All ages',
    popularity: 74,
    ticketTiers: [
      { name: 'Standard', price: GHS(50), perks: ['Entry', 'Welcome drink'] },
      { name: 'Meet & Greet', price: GHS(200), perks: ['Signed merch', 'Photo with Camidoh'] },
    ],
  },
  {
    id: 'afro-nation-gh',
    title: 'Afro Nation Ghana',
    artistName: 'Multiple Artists',
    lineup: ['Burna Boy', 'Rema', 'Black Sherif', 'Asake'],
    image: IMG.beach,
    date: '2026-10-10T15:00:00',
    doorsTime: '3:00 PM',
    venue: 'Aburi Gardens',
    city: 'Aburi',
    region: 'Eastern',
    status: 'sold-out',
    category: 'Festival',
    description: 'The world’s biggest afrobeats festival lands in Ghana.',
    ageRestriction: '18+',
    popularity: 91,
    ticketTiers: [
      { name: 'General', price: GHS(500), soldOut: true },
      { name: 'VIP', price: GHS(1500), soldOut: true },
    ],
  },
]

// --- lookups -------------------------------------------------------------

export const getEvent = (id: string): Event | undefined => events.find((e) => e.id === id)

const byDate = (a: Event, b: Event) => a.date.localeCompare(b.date)

/** Chronological upcoming events. */
export const upcomingEvents = [...events].sort(byDate)

export const featuredEvent = [...events].sort((a, b) => (b.popularity ?? 0) - (a.popularity ?? 0))[0]

/** Distinct cities for the filter, ordered by event count. */
export const eventCities: string[] = Array.from(new Set(events.map((e) => e.city)))

export const eventsByCity = (city: string): Event[] =>
  city === 'All' ? upcomingEvents : upcomingEvents.filter((e) => e.city === city)

/** Lowest ticket price for "from ₵X" labels. */
export function lowestTicketPrice(event: Event): number {
  return Math.min(...event.ticketTiers.map((t) => t.price.amount))
}
