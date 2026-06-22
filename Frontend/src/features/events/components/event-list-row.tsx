import { Link } from '@tanstack/react-router'
import { MapPin, Ticket } from 'lucide-react'
import type { Event } from '../../../types'
import { formatPrice } from '../../../lib/format'
import { lowestTicketPrice } from '../../../lib/event-data'
import { StatusBadge, eventMonth, eventDay } from '../event-ui'

export function EventListRow({ event }: { event: Event }) {
  return (
    <Link
      to="/event/$eventId"
      params={{ eventId: event.id }}
      className="flex items-center gap-4 p-3 rounded-2xl hover:bg-gray-100 dark:hover:bg-white/5 transition-colors group"
    >
      {/* Date badge */}
      <div className="flex flex-col items-center justify-center w-14 h-14 rounded-xl bg-beatz-light-surface-2 dark:bg-beatz-dark-surface-2 shrink-0">
        <span className="text-[10px] font-bold uppercase text-gray-500 dark:text-gray-300 leading-none">{eventMonth(event.date)}</span>
        <span className="text-xl font-bold text-beatz-dark-bg dark:text-white leading-none mt-0.5">{eventDay(event.date)}</span>
      </div>

      <div className="w-14 h-14 rounded-lg overflow-hidden shrink-0 hidden sm:block">
        <img src={event.image} alt={event.title} className="w-full h-full object-cover" />
      </div>

      <div className="flex flex-col flex-1 min-w-0">
        <span className="font-bold text-beatz-dark-bg dark:text-white truncate group-hover:text-beatz-green transition-colors">{event.title}</span>
        <span className="text-sm text-gray-500 dark:text-gray-300 flex items-center gap-1 truncate">
          <MapPin size={12} className="shrink-0" /> {event.venue}, {event.city}
        </span>
        <span className="text-[10px] font-bold uppercase tracking-widest text-gray-400 mt-0.5">{event.category}</span>
      </div>

      <div className="hidden sm:flex flex-col items-end gap-1.5 shrink-0">
        <StatusBadge status={event.status} />
        <span className="text-sm font-mono font-bold text-beatz-green">
          from {formatPrice({ amount: lowestTicketPrice(event), currency: 'GHS' })}
        </span>
      </div>

      <span className="hidden md:inline-flex h-9 px-4 rounded-full bg-beatz-green text-black text-xs font-bold items-center gap-1.5 shrink-0 group-hover:scale-105 transition-transform">
        <Ticket size={14} /> Tickets
      </span>
    </Link>
  )
}
