/**
 * Mock podcast catalog for BeatzClik — Ghana / Africa focused shows.
 * Episodes can be played through the music player via `episodeToTrack`.
 *
 * Monetization mirrors the musician model: a free feed for reach, plus premium
 * / early-access episodes sold buy-to-own, an optional season pass, and tips.
 */

import type { Podcast, PodcastCategory, PodcastEpisode, Track } from '../types'

const GHS = (amount: number) => ({ amount, currency: 'GHS' as const })

const IMG = {
  mic: 'https://images.unsplash.com/photo-1478737270239-2f02b77fc618?q=80&w=600&auto=format&fit=crop',
  studio: 'https://images.unsplash.com/photo-1590602847861-f357a9332bbc?q=80&w=600&auto=format&fit=crop',
  talk: 'https://images.unsplash.com/photo-1521119989659-a83eee488004?q=80&w=600&auto=format&fit=crop',
  news: 'https://images.unsplash.com/photo-1504711434969-e33886168f5c?q=80&w=600&auto=format&fit=crop',
  money: 'https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?q=80&w=600&auto=format&fit=crop',
  ball: 'https://images.unsplash.com/photo-1431324155629-1a6deb1dec8d?q=80&w=600&auto=format&fit=crop',
  tech: 'https://images.unsplash.com/photo-1518770660439-4636190af475?q=80&w=600&auto=format&fit=crop',
  culture: 'https://images.unsplash.com/photo-1516280440614-37939bbacd81?q=80&w=600&auto=format&fit=crop',
  story: 'https://images.unsplash.com/photo-1457369804613-52c61a468e7d?q=80&w=600&auto=format&fit=crop',
}

export const podcastCategories: PodcastCategory[] = [
  'News & Politics',
  'Comedy',
  'Business',
  'Sports',
  'Culture',
  'Tech',
  'Health',
  'Storytelling',
]

export const podcasts: Podcast[] = [
  {
    id: 'sincerely-accra',
    title: 'Sincerely, Accra',
    publisher: 'YGTV Africa',
    image: IMG.culture,
    category: 'Culture',
    description: 'Honest conversations about life, love and growing up in the city.',
    episodeCount: 142,
    popularity: 98,
    seasonPassPrice: GHS(12),
    supportsTips: true,
  },
  {
    id: 'front-page-gh',
    title: 'Front Page',
    publisher: 'Citi Newsroom',
    image: IMG.news,
    category: 'News & Politics',
    description: 'A daily breakdown of the stories shaping Ghana.',
    episodeCount: 410,
    popularity: 95,
    seasonPassPrice: GHS(10),
    supportsTips: true,
  },
  {
    id: 'cedi-talk',
    title: 'Cedi Talk',
    publisher: 'Accra Business Network',
    image: IMG.money,
    category: 'Business',
    description: 'Money, markets and building wealth in Ghana.',
    episodeCount: 88,
    popularity: 90,
    seasonPassPrice: GHS(15),
    supportsTips: true,
  },
  {
    id: 'konnect-comedy',
    title: 'The Konnect',
    publisher: 'Konnect Media',
    image: IMG.talk,
    category: 'Comedy',
    description: 'Three friends, zero filter, plenty of laughs.',
    episodeCount: 64,
    popularity: 88,
    supportsTips: true,
  },
  {
    id: 'black-stars-pod',
    title: 'Black Stars Breakdown',
    publisher: 'Joy Sports',
    image: IMG.ball,
    category: 'Sports',
    description: 'Everything Ghana football, from the GPL to the Black Stars.',
    episodeCount: 120,
    popularity: 84,
    supportsTips: true,
  },
  {
    id: 'tech-nkwa',
    title: 'Tech Nkwa',
    publisher: 'Accra Tech Hub',
    image: IMG.tech,
    category: 'Tech',
    description: 'Africa’s startup and technology scene, decoded.',
    episodeCount: 52,
    popularity: 78,
    supportsTips: true,
  },
  {
    id: 'asaase-stories',
    title: 'Asaase Stories',
    publisher: 'Asaase Radio',
    image: IMG.story,
    category: 'Storytelling',
    description: 'Folktales and true stories from across the motherland.',
    episodeCount: 37,
    popularity: 72,
    supportsTips: true,
  },
  {
    id: 'well-being-gh',
    title: 'Body & Soul GH',
    publisher: 'Wellness Accra',
    image: IMG.mic,
    category: 'Health',
    description: 'Practical wellness and mental health for busy Ghanaians.',
    episodeCount: 45,
    popularity: 68,
    supportsTips: true,
  },
]

export const episodes: PodcastEpisode[] = [
  // Sincerely, Accra — the showcase feed (free + premium + early access)
  {
    id: 'ep-accra-6',
    podcastId: 'sincerely-accra',
    title: 'Healing out loud',
    showTitle: 'Sincerely, Accra',
    image: IMG.culture,
    duration: 3120,
    publishedAt: '2026-06-18',
    episodeNumber: 48,
    description: 'A raw conversation about therapy, faith and forgiveness.',
    isPremium: true,
    isEarlyAccess: true,
    publicAt: '2026-06-27',
    price: GHS(5),
  },
  {
    id: 'ep-accra-5',
    podcastId: 'sincerely-accra',
    title: 'Moving back home after years abroad',
    showTitle: 'Sincerely, Accra',
    image: IMG.culture,
    duration: 2940,
    publishedAt: '2026-06-16',
    episodeNumber: 47,
    description: 'The reverse culture shock nobody warns you about.',
  },
  {
    id: 'ep-accra-4',
    podcastId: 'sincerely-accra',
    title: 'Dating in your 30s in Accra',
    showTitle: 'Sincerely, Accra',
    image: IMG.culture,
    duration: 3360,
    publishedAt: '2026-06-09',
    episodeNumber: 46,
    description: 'Apps, aunties and the pressure to settle down.',
    isPremium: true,
    price: GHS(3),
  },
  {
    id: 'ep-accra-3',
    podcastId: 'sincerely-accra',
    title: 'Money and friendships',
    showTitle: 'Sincerely, Accra',
    image: IMG.culture,
    duration: 3000,
    publishedAt: '2026-06-02',
    episodeNumber: 45,
    description: 'What happens when your circle grows at different speeds.',
    isPremium: true,
    isOwned: true,
    price: GHS(3),
  },
  {
    id: 'ep-accra-2',
    podcastId: 'sincerely-accra',
    title: 'Therapy, the Ghanaian way',
    showTitle: 'Sincerely, Accra',
    image: IMG.culture,
    duration: 2820,
    publishedAt: '2026-05-26',
    episodeNumber: 44,
  },
  {
    id: 'ep-accra-1',
    podcastId: 'sincerely-accra',
    title: 'Season 2 opener: starting over',
    showTitle: 'Sincerely, Accra',
    image: IMG.culture,
    duration: 2700,
    publishedAt: '2026-05-19',
    episodeNumber: 43,
  },

  // Front Page — news (mostly free, one premium deep dive)
  {
    id: 'ep-front-2',
    podcastId: 'front-page-gh',
    title: 'The 2026 budget, explained',
    showTitle: 'Front Page',
    image: IMG.news,
    duration: 1620,
    publishedAt: '2026-06-18',
    description: 'What the new budget means for your pocket.',
  },
  {
    id: 'ep-front-1',
    podcastId: 'front-page-gh',
    title: 'Ad-free deep dive: the cedi vs the dollar',
    showTitle: 'Front Page',
    image: IMG.news,
    duration: 2580,
    publishedAt: '2026-06-11',
    description: 'Extended, ad-free analysis for supporters.',
    isPremium: true,
    price: GHS(4),
  },

  // Cedi Talk — business (premium masterclass)
  {
    id: 'ep-cedi-2',
    podcastId: 'cedi-talk',
    title: 'Saving in cedis vs dollars',
    showTitle: 'Cedi Talk',
    image: IMG.money,
    duration: 2280,
    publishedAt: '2026-06-15',
    description: 'How to protect your savings against inflation.',
  },
  {
    id: 'ep-cedi-1',
    podcastId: 'cedi-talk',
    title: 'Masterclass: building a MoMo side hustle',
    showTitle: 'Cedi Talk',
    image: IMG.money,
    duration: 3300,
    publishedAt: '2026-06-08',
    description: 'A step-by-step premium workshop episode.',
    isPremium: true,
    price: GHS(6),
  },

  // Single free episodes for the rest
  {
    id: 'ep-konnect-1',
    podcastId: 'konnect-comedy',
    title: 'Wedding season survival guide',
    showTitle: 'The Konnect',
    image: IMG.talk,
    duration: 3300,
    publishedAt: '2026-06-14',
    description: 'Aso ebi, MCs and the contribution wahala.',
  },
  {
    id: 'ep-stars-1',
    podcastId: 'black-stars-pod',
    title: 'Can the Black Stars bounce back?',
    showTitle: 'Black Stars Breakdown',
    image: IMG.ball,
    duration: 2700,
    publishedAt: '2026-06-17',
    description: 'Breaking down the squad ahead of the qualifiers.',
  },
  {
    id: 'ep-tech-1',
    podcastId: 'tech-nkwa',
    title: 'Mobile money and the future of payments',
    showTitle: 'Tech Nkwa',
    image: IMG.tech,
    duration: 2100,
    publishedAt: '2026-06-12',
    description: 'How MoMo reshaped commerce across West Africa.',
  },
  {
    id: 'ep-asaase-1',
    podcastId: 'asaase-stories',
    title: 'Why the tortoise has a cracked shell',
    showTitle: 'Asaase Stories',
    image: IMG.story,
    duration: 1500,
    publishedAt: '2026-06-10',
    description: 'A retelling of the classic Ananse-era folktale.',
  },
  {
    id: 'ep-body-1',
    podcastId: 'well-being-gh',
    title: 'Beating burnout in a hustle culture',
    showTitle: 'Body & Soul GH',
    image: IMG.mic,
    duration: 1980,
    publishedAt: '2026-06-13',
    description: 'Rest is productive too.',
  },
]

// --- access logic --------------------------------------------------------

/** Whether the current user can play an episode (free, owned, or now-public). */
export function episodeAccessible(ep: PodcastEpisode): boolean {
  if (ep.isOwned) return true
  if (ep.isEarlyAccess) return ep.publicAt ? new Date(ep.publicAt) <= new Date() : false
  return !ep.isPremium
}

// --- lookups -------------------------------------------------------------

const byNewest = (a: PodcastEpisode, b: PodcastEpisode) => b.publishedAt.localeCompare(a.publishedAt)

export const getPodcast = (id: string): Podcast | undefined => podcasts.find((p) => p.id === id)

export const getShowEpisodes = (podcastId: string): PodcastEpisode[] =>
  episodes.filter((e) => e.podcastId === podcastId).sort(byNewest)

export const latestEpisode = (podcastId: string): PodcastEpisode | undefined => getShowEpisodes(podcastId)[0]

/** Newest episode the user can actually play (used for "Play" buttons). */
export const latestPlayable = (podcastId: string): PodcastEpisode | undefined =>
  getShowEpisodes(podcastId).find(episodeAccessible)

/** Most recent episodes across all shows. */
export const trendingEpisodes = [...episodes].sort(byNewest)

export const topShows = [...podcasts].sort((a, b) => (b.popularity ?? 0) - (a.popularity ?? 0))

/** Adapt an episode into a Track so it can flow through the music player. */
export function episodeToTrack(ep: PodcastEpisode): Track {
  return {
    id: ep.id,
    title: ep.title,
    artistId: ep.podcastId,
    artistName: ep.showTitle,
    duration: ep.duration,
    image: ep.image,
    ownership: 'free',
  }
}
