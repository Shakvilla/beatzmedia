import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useState } from 'react'
import { useQuery, useSuspenseQuery } from '@tanstack/react-query'
import { ArrowLeft, CalendarDays, MapPin, Clock, Users, Minus, Plus, Smartphone, Share2 } from 'lucide-react'
import { useToast } from '../components/ui/toast-provider'
import { useCart } from '../features/cart/cart-context'
import { TicketTierSelector } from '../features/events/components/ticket-tier-selector'
import { StatusBadge, formatEventDate } from '../features/events/event-ui'
import { eventQuery } from '../lib/api/queries/events'
import { artistQuery } from '../lib/api/queries/catalog'
import { formatPrice } from '../lib/format'

export const Route = createFileRoute('/event/$eventId')({
  loader: async ({ context: { queryClient }, params: { eventId } }) => {
    const event = await queryClient.ensureQueryData(eventQuery(eventId))
    if (event.artistId) {
      await queryClient.ensureQueryData(artistQuery(event.artistId))
    }
  },
  component: EventDetailPage,
  errorComponent: () => (
    <div className="flex flex-col items-center justify-center text-center gap-4 py-32">
      <h1 className="text-title text-beatz-dark-bg dark:text-white">Event not found</h1>
      <Link to="/events" className="h-11 px-6 rounded-full bg-beatz-green text-black font-bold flex items-center">
        Back to events
      </Link>
    </div>
  ),
})

function EventDetailPage() {
  const { eventId } = Route.useParams()
  const { data: event } = useSuspenseQuery(eventQuery(eventId))
  const { data: artist } = useQuery({ ...artistQuery(event.artistId ?? ''), enabled: !!event.artistId })
  const { toast } = useToast()
  const { addItem } = useCart()
  const navigate = useNavigate()

  const firstAvailable = event.ticketTiers.findIndex((t) => !t.soldOut)
  const [tierIndex, setTierIndex] = useState(firstAvailable === -1 ? 0 : firstAvailable)
  const [qty, setQty] = useState(1)

  const soldOut = event.status === 'sold-out'
  const tier = event.ticketTiers[tierIndex]
  const total = (tier?.price.amount ?? 0) * qty

  const buyTickets = () => {
    addItem({
      id: `ticket:${event.id}:${tier.name}`,
      kind: 'ticket',
      title: `${event.title} — ${tier.name}`,
      subtitle: `${event.venue}, ${event.city}`,
      image: event.image,
      price: tier.price,
      quantity: qty,
      stackable: true,
    })
    toast(`${qty} × ${tier.name} ticket${qty > 1 ? 's' : ''} added to cart`, 'success')
    navigate({ to: '/cart' })
  }

  return (
    <div className="flex flex-col -mt-20 -mx-4 md:-mx-8">
      {/* Hero */}
      <div className="relative px-4 md:px-8 pt-24 md:pt-28 pb-8 overflow-hidden">
        <div className="absolute inset-0">
          <img src={event.image} alt="" className="w-full h-full object-cover blur-3xl scale-125 opacity-50 dark:opacity-35" />
          <div className="absolute inset-0 bg-gradient-to-b from-white/30 dark:from-black/40 via-beatz-light-bg/85 dark:via-beatz-dark-bg/85 to-beatz-light-bg dark:to-beatz-dark-bg" />
        </div>

        <div className="relative z-10 flex flex-col gap-6">
          <Link to="/events" className="flex items-center gap-2 text-sm font-bold text-gray-500 dark:text-gray-300 hover:text-beatz-dark-bg dark:hover:text-white transition-colors w-fit">
            <ArrowLeft size={16} /> Back to events
          </Link>

          <div className="flex flex-col md:flex-row items-center md:items-end gap-8">
            <div className="w-48 h-48 lg:w-60 lg:h-60 shrink-0 rounded-2xl overflow-hidden shadow-2xl ring-1 ring-black/10 dark:ring-white/10">
              <img src={event.image} alt={event.title} className="w-full h-full object-cover" />
            </div>

            <div className="flex flex-col items-center md:items-start gap-3 min-w-0">
              <div className="flex items-center gap-3">
                <StatusBadge status={event.status} />
                <span className="text-xs font-bold tracking-[0.3em] uppercase text-beatz-green">{event.category}</span>
              </div>
              <h1 className="text-3xl sm:text-4xl lg:text-6xl font-bold text-beatz-dark-bg dark:text-white tracking-tight leading-[1.05] text-center md:text-left break-words">
                {event.title}
              </h1>
              <div className="flex flex-col items-center md:items-start gap-1.5 text-beatz-dark-bg dark:text-white">
                <span className="flex items-center gap-2"><CalendarDays size={16} className="text-gray-400" /> {formatEventDate(event.date)}{event.doorsTime ? ` · Doors ${event.doorsTime}` : ''}</span>
                <span className="flex items-center gap-2"><MapPin size={16} className="text-gray-400" /> {event.venue}, {event.city}</span>
                {artist && (
                  <Link to="/artist/$artistId" params={{ artistId: artist.id }} className="flex items-center gap-2 hover:underline">
                    <Users size={16} className="text-gray-400" /> {event.artistName}
                  </Link>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Body */}
      <div className="px-4 md:px-8 pt-4 pb-16 grid grid-cols-1 lg:grid-cols-3 gap-6 lg:gap-10">
        {/* Left: details */}
        <div className="lg:col-span-2 flex flex-col gap-8">
          {event.description && (
            <section className="flex flex-col gap-3">
              <h2 className="text-title text-beatz-dark-bg dark:text-white">About</h2>
              <p className="text-gray-600 dark:text-gray-300 leading-relaxed">{event.description}</p>
            </section>
          )}

          {event.lineup && event.lineup.length > 0 && (
            <section className="flex flex-col gap-3">
              <h2 className="text-title text-beatz-dark-bg dark:text-white">Lineup</h2>
              <div className="flex flex-wrap gap-2">
                <span className="px-4 py-2 rounded-full bg-beatz-green/10 text-beatz-green text-sm font-bold">{event.artistName} (Headliner)</span>
                {event.lineup.map((act) => (
                  <span key={act} className="px-4 py-2 rounded-full bg-white dark:bg-beatz-dark-surface-2 border border-gray-200 dark:border-transparent text-beatz-dark-bg dark:text-white text-sm font-bold">
                    {act}
                  </span>
                ))}
              </div>
            </section>
          )}

          <section className="flex flex-col gap-3">
            <h2 className="text-title text-beatz-dark-bg dark:text-white">Good to know</h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <InfoCard icon={<Clock size={18} />} label="Doors open" value={event.doorsTime ?? 'TBA'} />
              <InfoCard icon={<Users size={18} />} label="Age" value={event.ageRestriction ?? 'All ages'} />
              <InfoCard icon={<MapPin size={18} />} label="Venue" value={`${event.venue}, ${event.city}`} />
              <InfoCard icon={<CalendarDays size={18} />} label="Date" value={formatEventDate(event.date)} />
            </div>
          </section>
        </div>

        {/* Right: ticket purchase */}
        <div className="lg:sticky lg:top-24 h-fit flex flex-col gap-5 bg-white dark:bg-beatz-dark-surface-2 border border-gray-100 dark:border-transparent rounded-3xl p-6 shadow-xl">
          <div className="flex items-center justify-between">
            <h2 className="text-xl font-bold text-beatz-dark-bg dark:text-white">Tickets</h2>
            <button className="w-9 h-9 rounded-full border border-gray-200 dark:border-white/15 flex items-center justify-center text-gray-500 dark:text-gray-300 hover:text-beatz-dark-bg dark:hover:text-white transition-colors" aria-label="Share">
              <Share2 size={16} />
            </button>
          </div>

          {soldOut ? (
            <div className="flex flex-col items-center gap-2 py-8 text-center">
              <span className="text-2xl font-bold text-beatz-red">Sold out</span>
              <span className="text-sm text-gray-500 dark:text-gray-300">Join the waitlist to be notified if tickets are released.</span>
              <button className="mt-3 h-11 px-6 rounded-full border border-gray-300 dark:border-white/20 text-beatz-dark-bg dark:text-white font-bold text-sm hover:bg-gray-100 dark:hover:bg-white/10 transition-colors">
                Join waitlist
              </button>
            </div>
          ) : (
            <>
              <TicketTierSelector tiers={event.ticketTiers} value={tierIndex} onChange={setTierIndex} />

              <div className="flex items-center justify-between pt-2">
                <span className="text-sm font-bold text-beatz-dark-bg dark:text-white">Quantity</span>
                <div className="flex items-center gap-3">
                  <button
                    onClick={() => setQty((q) => Math.max(1, q - 1))}
                    className="w-9 h-9 rounded-full border border-gray-300 dark:border-white/20 flex items-center justify-center text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-white/10 transition-colors"
                    aria-label="Decrease quantity"
                  >
                    <Minus size={16} />
                  </button>
                  <span className="w-6 text-center font-mono font-bold text-beatz-dark-bg dark:text-white">{qty}</span>
                  <button
                    onClick={() => setQty((q) => Math.min(10, q + 1))}
                    className="w-9 h-9 rounded-full border border-gray-300 dark:border-white/20 flex items-center justify-center text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-white/10 transition-colors"
                    aria-label="Increase quantity"
                  >
                    <Plus size={16} />
                  </button>
                </div>
              </div>

              <div className="flex items-center justify-between border-t border-gray-100 dark:border-white/5 pt-4">
                <span className="text-sm font-bold uppercase tracking-widest text-gray-500 dark:text-gray-300">Total</span>
                <span className="text-2xl font-mono font-bold text-beatz-green">{formatPrice({ amount: total, currency: 'GHS' })}</span>
              </div>

              <button
                onClick={buyTickets}
                className="h-12 rounded-full bg-beatz-green text-black font-bold flex items-center justify-center gap-2 hover:scale-[1.02] transition-transform"
              >
                <Smartphone size={18} /> Buy with MoMo
              </button>
              <p className="text-center text-[10px] text-gray-500 dark:text-gray-300">E-tickets sent instantly. Pay with MoMo, card or bank transfer.</p>
            </>
          )}
        </div>
      </div>
    </div>
  )
}

function InfoCard({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="flex items-center gap-3 p-4 rounded-xl bg-white dark:bg-beatz-dark-surface-2 border border-gray-100 dark:border-transparent">
      <div className="w-10 h-10 rounded-lg bg-gray-100 dark:bg-white/5 flex items-center justify-center text-gray-500 dark:text-gray-300 shrink-0">{icon}</div>
      <div className="flex flex-col min-w-0">
        <span className="text-[10px] font-bold uppercase tracking-widest text-gray-500 dark:text-gray-300">{label}</span>
        <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{value}</span>
      </div>
    </div>
  )
}
