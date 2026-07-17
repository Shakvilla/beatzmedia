import { Link } from '@tanstack/react-router'
import { MapPin } from 'lucide-react'
import type { Event } from '../../../types'
import { formatPrice } from '../../../lib/format'
import { StatusBadge, eventMonth, eventDay } from '../event-ui'

/** Lowest ticket tier price for "from ₵X" labels. Returns undefined when there are no tiers. */
function lowestTicketPrice(event: Event) {
  if (event.ticketTiers.length === 0) return undefined
  return event.ticketTiers.reduce((lowest, tier) => (tier.price.amount < lowest.amount ? tier.price : lowest), event.ticketTiers[0].price)
}

export function EventCard({ event }: { event: Event }) {
  const price = lowestTicketPrice(event)
  return (
    <Link to="/event/$eventId" params={{ eventId: event.id }} className="group flex flex-col cursor-pointer">
      <div className="relative aspect-[3/4] rounded-xl overflow-hidden ring-1 ring-black/5 dark:ring-white/5 shadow-sm transition-all duration-300 group-hover:shadow-2xl">
        <img src={event.image} alt={event.title} className="w-full h-full object-cover transition-transform duration-[600ms] ease-out group-hover:scale-105" />
        <div className="absolute inset-0 bg-gradient-to-t from-black/85 via-black/20 to-transparent" />

        <div className="absolute top-3 left-3 flex flex-col items-center justify-center w-12 h-12 rounded-lg bg-white/95 text-black shadow-lg">
          <span className="text-[9px] font-bold uppercase leading-none">{eventMonth(event.date)}</span>
          <span className="text-lg font-bold leading-none mt-0.5">{eventDay(event.date)}</span>
        </div>
        <div className="absolute top-3 right-3">
          <StatusBadge status={event.status} className="bg-black/50 backdrop-blur" />
        </div>

        <div className="absolute bottom-0 left-0 right-0 p-4 flex flex-col gap-1">
          <h3 className="font-bold text-white truncate group-hover:text-beatz-green transition-colors">{event.title}</h3>
          <span className="text-xs text-white/70 flex items-center gap-1 truncate">
            <MapPin size={11} className="shrink-0" /> {event.venue}, {event.city}
          </span>
          {price && (
            <span className="text-sm font-mono font-bold text-beatz-green mt-1">
              from {formatPrice(price)}
            </span>
          )}
        </div>
      </div>
    </Link>
  )
}
