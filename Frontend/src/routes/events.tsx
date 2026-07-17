import { createFileRoute, Link } from '@tanstack/react-router'
import { useState } from 'react'
import { useSuspenseQuery } from '@tanstack/react-query'
import { CalendarDays, MapPin, Ticket } from 'lucide-react'
import { MediaRail } from '../features/discover/components/media-rail'
import { EventCard } from '../features/events/components/event-card'
import { EventListRow } from '../features/events/components/event-list-row'
import { StatusBadge, formatEventDate } from '../features/events/event-ui'
import { eventsListQuery } from '../lib/api/queries/events'
import { formatPrice } from '../lib/format'
import { cn } from '../utils/cn'
import type { Event } from '../types'

export const Route = createFileRoute('/events')({
  loader: ({ context: { queryClient } }) => queryClient.ensureQueryData(eventsListQuery()),
  component: EventsComponent,
})

const RAIL_ITEM = 'snap-start shrink-0 w-44 sm:w-52 lg:w-56'

/** Lowest ticket price for "from ₵X" labels. */
function lowestTicketPrice(event: Event): number {
  return Math.min(...event.ticketTiers.map((t) => t.price.amount))
}

function EventsComponent() {
  const { data: events } = useSuspenseQuery(eventsListQuery())
  const [city, setCity] = useState('All')

  const upcomingEvents = [...events].sort((a, b) => a.date.localeCompare(b.date))
  const featuredEvent = [...events].sort((a, b) => (b.popularity ?? 0) - (a.popularity ?? 0))[0]
  const eventCities = Array.from(new Set(events.map((e) => e.city)))
  const list = city === 'All' ? upcomingEvents : upcomingEvents.filter((e) => e.city === city)
  const festivals = upcomingEvents.filter((e) => e.category === 'Festival')

  if (!featuredEvent) {
    return (
      <div className="flex flex-col items-center justify-center text-center gap-4 py-32">
        <h1 className="text-title text-beatz-dark-bg dark:text-white">No events yet</h1>
        <p className="text-gray-500 dark:text-gray-300 max-w-md">Check back soon for concerts, festivals and shows.</p>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-12">
      <div className="flex flex-col gap-2">
        <span className="flex items-center gap-2 text-xs font-bold tracking-[0.3em] uppercase text-beatz-green">
          <CalendarDays size={14} /> Live Events
        </span>
        <h1 className="text-display tracking-tight text-beatz-dark-bg dark:text-white">See them live</h1>
        <p className="text-gray-500 dark:text-gray-300 max-w-2xl">
          Concerts, festivals and shows across Ghana — grab tickets and pay with MoMo.
        </p>
      </div>

      {/* Featured event */}
      <Link
        to="/event/$eventId"
        params={{ eventId: featuredEvent.id }}
        className="relative overflow-hidden rounded-3xl group min-h-[360px] lg:min-h-[420px] flex"
      >
        <img src={featuredEvent.image} alt={featuredEvent.title} className="absolute inset-0 w-full h-full object-cover transition-transform duration-[800ms] ease-out group-hover:scale-105" />
        <div className="absolute inset-0 bg-gradient-to-r from-black/90 via-black/55 to-transparent" />
        <div className="relative z-10 flex flex-col justify-end gap-4 p-10 lg:p-14 max-w-2xl">
          <div className="flex items-center gap-3">
            <StatusBadge status={featuredEvent.status} className="bg-black/50 backdrop-blur" />
            <span className="text-xs font-bold tracking-[0.3em] uppercase text-white/70">{featuredEvent.category}</span>
          </div>
          <h2 className="text-3xl sm:text-4xl lg:text-6xl font-bold text-white tracking-tight leading-[1.05]">{featuredEvent.title}</h2>
          <div className="flex flex-col gap-1 text-gray-200">
            <span className="flex items-center gap-2"><CalendarDays size={16} /> {formatEventDate(featuredEvent.date)}</span>
            <span className="flex items-center gap-2"><MapPin size={16} /> {featuredEvent.venue}, {featuredEvent.city}</span>
          </div>
          <div className="flex items-center gap-4 mt-2">
            <span className="h-12 px-7 rounded-full bg-beatz-green text-black font-bold flex items-center gap-2 group-hover:scale-105 transition-transform">
              <Ticket size={18} /> Tickets from {formatPrice({ amount: lowestTicketPrice(featuredEvent), currency: 'GHS' })}
            </span>
          </div>
        </div>
      </Link>

      {/* City filter */}
      <div className="flex items-center gap-2 flex-wrap">
        {(['All', ...eventCities]).map((c) => (
          <button
            key={c}
            onClick={() => setCity(c)}
            className={cn(
              'px-4 py-2 rounded-full text-sm font-bold transition-colors',
              city === c
                ? 'bg-beatz-dark-bg dark:bg-white text-white dark:text-black'
                : 'bg-white dark:bg-beatz-dark-surface-2 text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-beatz-dark-surface-3',
            )}
          >
            {c}
          </button>
        ))}
      </div>

      {/* Upcoming list */}
      <section className="flex flex-col gap-5">
        <div className="flex items-end justify-between gap-4 border-b border-gray-200 dark:border-white/5 pb-2">
          <h2 className="text-title text-beatz-dark-bg dark:text-white">Upcoming events</h2>
          <span className="text-xs font-mono uppercase tracking-widest text-gray-500 dark:text-gray-300">
            {list.length} {list.length === 1 ? 'event' : 'events'}{city !== 'All' ? ` in ${city}` : ''}
          </span>
        </div>
        <div className="flex flex-col gap-1">
          {list.map((event) => (
            <EventListRow key={event.id} event={event} />
          ))}
          {list.length === 0 && <p className="text-gray-500 dark:text-gray-300 py-8 text-center">No events in {city} yet.</p>}
        </div>
      </section>

      {/* Festivals rail */}
      {festivals.length > 0 && (
        <MediaRail title="Festivals & tours" subtitle="The big ones worth travelling for">
          {festivals.map((event) => (
            <div key={event.id} className={RAIL_ITEM}>
              <EventCard event={event} />
            </div>
          ))}
        </MediaRail>
      )}
    </div>
  )
}
