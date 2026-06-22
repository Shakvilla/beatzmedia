/**
 * Mock catalog for the Music Store.
 *
 * Mirrors the shapes in `src/types`. Grouped by store tab (Hi-Fi, Beats, Merch,
 * Exclusives). Like `mock-data.ts`, the lookup/filter helpers here are a stand-in
 * for real API calls and can be swapped for TanStack Query later.
 */

import type { StoreItem, StoreSort, LicenseTier } from '../types'

const GHS = (amount: number) => ({ amount, currency: 'GHS' as const })

const IMG = {
  bsherif: 'https://images.unsplash.com/photo-1619983081563-430f63602796?q=80&w=600&auto=format&fit=crop',
  burna: 'https://images.unsplash.com/photo-1493225255756-d9584f8606e9?q=80&w=600&auto=format&fit=crop',
  asake: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=600&auto=format&fit=crop',
  king: 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=600&auto=format&fit=crop',
  lasmid: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=600&auto=format&fit=crop',
  camidoh: 'https://images.unsplash.com/photo-1504151932400-72d4384f0e6d?q=80&w=600&auto=format&fit=crop',
  rema: 'https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?q=80&w=600&auto=format&fit=crop',
  villain: 'https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?q=80&w=600&auto=format&fit=crop',
  studio: 'https://images.unsplash.com/photo-1598488035139-bdbb2231ce04?q=80&w=600&auto=format&fit=crop',
  tee: 'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?q=80&w=600&auto=format&fit=crop',
  hoodie: 'https://images.unsplash.com/photo-1556821840-3a63f95609a7?q=80&w=600&auto=format&fit=crop',
  cap: 'https://images.unsplash.com/photo-1588850561407-ed78c282e89b?q=80&w=600&auto=format&fit=crop',
  vinyl: 'https://images.unsplash.com/photo-1539375665275-f9de415ef9ac?q=80&w=600&auto=format&fit=crop',
  concert: 'https://images.unsplash.com/photo-1470229722913-7c0e2dbbafd3?q=80&w=600&auto=format&fit=crop',
}

/** Standard three-tier license ladder reused across beat products. */
function licenseLadder(base: number) {
  return [
    {
      tier: 'LEASE' as LicenseTier,
      label: 'Basic Lease',
      price: GHS(base),
      terms: 'MP3 • up to 10,000 streams',
      features: ['Tagged MP3 file', 'Up to 10,000 streams', 'Non-exclusive', '1 music video'],
    },
    {
      tier: 'PREMIUM' as LicenseTier,
      label: 'Premium Stems',
      price: GHS(base * 4),
      terms: 'WAV + stems • up to 100,000 streams',
      features: ['Untagged WAV + track stems', 'Up to 100,000 streams', 'Non-exclusive', 'Unlimited music videos'],
    },
    {
      tier: 'EXCLUSIVE' as LicenseTier,
      label: 'Exclusive',
      price: GHS(base * 20),
      terms: 'Full ownership transfer',
      features: ['WAV + stems + project file', 'Unlimited streams & sales', 'Exclusive — beat removed from store', 'Full ownership transfer'],
    },
  ]
}

// ---------------------------------------------------------------------------
// Hi-Fi: lossless tracks & mastered albums
// ---------------------------------------------------------------------------

export const hifiItems: StoreItem[] = [
  {
    id: 'hifi-love-damini',
    type: 'ALBUM',
    title: 'Love, Damini (Hi-Fi Master)',
    artistName: 'Burna Boy',
    artistId: 'burna-boy',
    image: IMG.burna,
    price: GHS(34.99),
    genre: 'Afrobeats',
    badges: ['HI-FI LOSSLESS'],
    quality: 'Lossless • 24-bit/192kHz',
    description: 'The full album remastered in studio-grade lossless audio.',
    popularity: 98,
    createdAt: '2026-05-02',
  },
  {
    id: 'hifi-iron-boy',
    type: 'ALBUM',
    title: 'Iron Boy (Hi-Fi Master)',
    artistName: 'Black Sherif',
    artistId: 'black-sherif',
    image: IMG.villain,
    price: GHS(29.99),
    genre: 'Drill',
    badges: ['HI-FI LOSSLESS'],
    quality: 'Lossless • 24-bit/96kHz',
    popularity: 95,
    createdAt: '2026-05-20',
  },
  {
    id: 'hifi-last-last',
    type: 'TRACK',
    title: 'Last Last (Hi-Fi)',
    artistName: 'Burna Boy',
    artistId: 'burna-boy',
    image: IMG.burna,
    price: GHS(4.5),
    genre: 'Afrobeats',
    badges: ['HI-FI LOSSLESS'],
    quality: 'Lossless • 24-bit/192kHz',
    popularity: 99,
    createdAt: '2026-04-15',
  },
  {
    id: 'hifi-sugarcane',
    type: 'TRACK',
    title: 'Sugarcane (Hi-Fi)',
    artistName: 'Camidoh',
    artistId: 'camidoh',
    image: IMG.camidoh,
    price: GHS(4.0),
    genre: 'R&B',
    badges: ['HI-FI LOSSLESS'],
    quality: 'Lossless • 16-bit/44.1kHz',
    popularity: 80,
    createdAt: '2026-03-30',
  },
  {
    id: 'hifi-terminator',
    type: 'TRACK',
    title: 'Terminator (Hi-Fi)',
    artistName: 'King Promise',
    artistId: 'king-promise',
    image: IMG.king,
    price: GHS(4.0),
    genre: 'Highlife',
    badges: ['HI-FI LOSSLESS'],
    quality: 'Lossless • 24-bit/96kHz',
    popularity: 76,
    createdAt: '2026-05-10',
  },
]

// ---------------------------------------------------------------------------
// Beats: licensable beats & stems
// ---------------------------------------------------------------------------

export const beatItems: StoreItem[] = [
  {
    id: 'beat-konongo-drill',
    type: 'BEAT_LICENSE',
    title: 'Konongo Drill Type Beat',
    artistName: 'Joker Nharnah',
    image: IMG.studio,
    price: GHS(50),
    genre: 'Drill',
    badges: ['STEMS INCLUDED'],
    description: 'Hard-hitting Ghanaian drill beat with live highlife guitars. 142 BPM, A minor.',
    popularity: 92,
    createdAt: '2026-05-18',
    licenseOptions: licenseLadder(50),
  },
  {
    id: 'beat-amapiano-log',
    type: 'BEAT_LICENSE',
    title: 'Amapiano Log Drum Groove',
    artistName: 'KeyzBeatz',
    image: IMG.asake,
    price: GHS(60),
    genre: 'Amapiano',
    badges: ['STEMS INCLUDED'],
    description: 'Smooth amapiano groove with rolling log drums and shakers. 112 BPM.',
    popularity: 88,
    createdAt: '2026-05-25',
    licenseOptions: licenseLadder(60),
  },
  {
    id: 'beat-highlife-soul',
    type: 'BEAT_LICENSE',
    title: 'Highlife Soul Instrumental',
    artistName: 'GuitarBoy GH',
    image: IMG.king,
    price: GHS(45),
    genre: 'Highlife',
    description: 'Warm highlife instrumental with palm-wine guitar licks. 96 BPM.',
    popularity: 70,
    createdAt: '2026-04-28',
    licenseOptions: licenseLadder(45),
  },
  {
    id: 'beat-afro-fusion',
    type: 'BEAT_LICENSE',
    title: 'Afro-Fusion Anthem',
    artistName: 'Atown TSB',
    image: IMG.rema,
    price: GHS(70),
    genre: 'Afrobeats',
    badges: ['STEMS INCLUDED'],
    description: 'Festival-sized afro-fusion anthem with big horns. 105 BPM.',
    popularity: 85,
    createdAt: '2026-06-01',
    licenseOptions: licenseLadder(70),
  },
]

// ---------------------------------------------------------------------------
// Merch: physical & digital
// ---------------------------------------------------------------------------

export const merchItems: StoreItem[] = [
  {
    id: 'merch-bsherif-tee',
    type: 'MERCH',
    title: 'Iron Boy Tour Tee',
    artistName: 'Black Sherif',
    artistId: 'black-sherif',
    image: IMG.tee,
    price: GHS(120),
    badges: ['OFFICIAL'],
    description: '100% cotton tee, screen-printed Iron Boy tour artwork.',
    popularity: 90,
    createdAt: '2026-05-12',
    stockRemaining: 42,
    variants: [
      { label: 'Size', options: ['S', 'M', 'L', 'XL', 'XXL'] },
      { label: 'Colour', options: ['Black', 'Cream'] },
    ],
  },
  {
    id: 'merch-burna-hoodie',
    type: 'MERCH',
    title: 'Outside Embroidered Hoodie',
    artistName: 'Burna Boy',
    artistId: 'burna-boy',
    image: IMG.hoodie,
    price: GHS(280),
    badges: ['OFFICIAL'],
    popularity: 84,
    createdAt: '2026-04-20',
    stockRemaining: 18,
    variants: [{ label: 'Size', options: ['S', 'M', 'L', 'XL'] }],
  },
  {
    id: 'merch-gh-cap',
    type: 'MERCH',
    title: 'Made in Ghana Snapback',
    artistName: 'BeatzClik',
    image: IMG.cap,
    price: GHS(85),
    popularity: 65,
    createdAt: '2026-03-15',
    variants: [{ label: 'Colour', options: ['Red', 'Gold', 'Black'] }],
  },
  {
    id: 'merch-villain-vinyl',
    type: 'MERCH',
    title: 'The Villain I Never Was — Vinyl',
    artistName: 'Black Sherif',
    artistId: 'black-sherif',
    image: IMG.vinyl,
    price: GHS(350),
    badges: ['LIMITED'],
    description: 'Limited gatefold double vinyl pressing.',
    popularity: 78,
    createdAt: '2026-05-30',
    stockRemaining: 7,
  },
]

// ---------------------------------------------------------------------------
// Exclusives: VIP experiences & limited drops
// ---------------------------------------------------------------------------

export const exclusiveItems: StoreItem[] = [
  {
    id: 'exclusive-meet-greet',
    type: 'EXCLUSIVE',
    title: 'VIP Meet & Greet — Accra',
    artistName: 'Black Sherif',
    artistId: 'black-sherif',
    image: IMG.concert,
    price: GHS(800),
    badges: ['LIMITED', 'VIP'],
    description: 'Backstage meet & greet plus signed merch at Independence Square.',
    popularity: 96,
    createdAt: '2026-06-05',
    dropsAt: '2026-07-09',
    stockRemaining: 12,
  },
  {
    id: 'exclusive-early-album',
    type: 'EXCLUSIVE',
    title: 'Early Access: Unreleased EP',
    artistName: 'Camidoh',
    artistId: 'camidoh',
    image: IMG.camidoh,
    price: GHS(50),
    badges: ['EARLY ACCESS'],
    description: 'Stream the new EP 7 days before public release.',
    popularity: 82,
    createdAt: '2026-06-10',
    dropsAt: '2026-06-25',
    stockRemaining: 500,
  },
  {
    id: 'exclusive-signed-vinyl',
    type: 'EXCLUSIVE',
    title: 'Signed Vinyl + Handwritten Lyrics',
    artistName: 'King Promise',
    artistId: 'king-promise',
    image: IMG.vinyl,
    price: GHS(600),
    badges: ['LIMITED'],
    popularity: 74,
    createdAt: '2026-05-28',
    stockRemaining: 25,
  },
]

export const allStoreItems: StoreItem[] = [
  ...hifiItems,
  ...beatItems,
  ...merchItems,
  ...exclusiveItems,
]

// ---------------------------------------------------------------------------
// Lookups & filtering
// ---------------------------------------------------------------------------

export const getStoreItem = (id: string): StoreItem | undefined =>
  allStoreItems.find((i) => i.id === id)

interface StoreFilters {
  q?: string
  genre?: string
  tier?: LicenseTier
  sort?: StoreSort
}

/** Filter + sort a list of store items by the URL-driven filters. */
export function filterStoreItems(items: StoreItem[], filters: StoreFilters = {}): StoreItem[] {
  const { q, genre, tier, sort = 'popular' } = filters
  let out = items

  if (q) {
    const needle = q.toLowerCase()
    out = out.filter((i) => `${i.title} ${i.artistName}`.toLowerCase().includes(needle))
  }
  if (genre) out = out.filter((i) => i.genre === genre)
  if (tier) out = out.filter((i) => i.licenseOptions?.some((l) => l.tier === tier))

  const price = (i: StoreItem) => i.price.amount
  const byNewest = (a: StoreItem, b: StoreItem) => (b.createdAt ?? '').localeCompare(a.createdAt ?? '')

  switch (sort) {
    case 'newest':
      return [...out].sort(byNewest)
    case 'price-asc':
      return [...out].sort((a, b) => price(a) - price(b))
    case 'price-desc':
      return [...out].sort((a, b) => price(b) - price(a))
    default:
      return [...out].sort((a, b) => (b.popularity ?? 0) - (a.popularity ?? 0))
  }
}

/** Lowest price across a beat's license tiers (for "from ₵X" display). */
export function lowestLicensePrice(item: StoreItem): number {
  if (!item.licenseOptions?.length) return item.price.amount
  return Math.min(...item.licenseOptions.map((l) => l.price.amount))
}
