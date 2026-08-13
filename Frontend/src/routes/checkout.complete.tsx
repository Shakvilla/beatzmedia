import { createFileRoute, Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Check, Clock, Download, Loader2, XCircle } from 'lucide-react'
import { orderQuery } from '../lib/api/queries/commerce'
import { formatPrice } from '../lib/format'

interface CheckoutCompleteSearch {
  orderId?: string
}

export const Route = createFileRoute('/checkout/complete')({
  validateSearch: (search: Record<string, unknown>): CheckoutCompleteSearch => ({
    orderId: typeof search.orderId === 'string' ? search.orderId : undefined,
  }),
  component: CheckoutCompleteComponent,
})

/**
 * How long to wait for settlement before telling the fan we haven't heard back.
 * A MoMo prompt lapses after about a minute, so an order still `pending` well past
 * that will almost never resolve on its own — polling it forever leaves the fan on a
 * spinner with no idea whether they were charged.
 */
const AUTHORIZE_TIMEOUT_MS = 120_000
const POLL_INTERVAL_MS = 2000

function CheckoutCompleteComponent() {
  const { orderId } = Route.useSearch()
  const [timedOut, setTimedOut] = useState(false)

  // Restart the clock if the fan retries with a different order.
  useEffect(() => {
    setTimedOut(false)
    const t = setTimeout(() => setTimedOut(true), AUTHORIZE_TIMEOUT_MS)
    return () => clearTimeout(t)
  }, [orderId])

  const { data: order, isLoading, isError } = useQuery({
    ...orderQuery(orderId ?? ''),
    enabled: !!orderId,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      // Stop polling once we've given up, so a stranded order doesn't hammer the API forever.
      return status === 'pending' && !timedOut ? POLL_INTERVAL_MS : false
    },
  })

  if (!orderId) {
    return (
      <div className="flex flex-col items-center justify-center text-center gap-4 py-32">
        <h1 className="text-title text-beatz-dark-bg dark:text-white">No recent order</h1>
        <Link to="/" className="h-11 px-6 rounded-full bg-beatz-green text-black font-bold flex items-center">Back to home</Link>
      </div>
    )
  }

  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center text-center gap-4 py-32">
        <div className="w-16 h-16 rounded-full bg-red-500/10 flex items-center justify-center">
          <XCircle className="text-red-500" size={32} />
        </div>
        <h1 className="text-title text-beatz-dark-bg dark:text-white">We couldn't load this order</h1>
        <p className="text-gray-500 dark:text-gray-300 max-w-sm">
          Something went wrong retrieving your order. Your cart has not been charged again — check your library or try again.
        </p>
        <Link to="/cart" className="h-11 px-6 rounded-full bg-beatz-green text-black font-bold flex items-center">Back to cart</Link>
      </div>
    )
  }

  if (isLoading || !order) {
    return <AuthorizingState />
  }

  if (order.status === 'pending') {
    // Still waiting, but past the point where a MoMo prompt would realistically land.
    // Say so honestly and give the fan somewhere to go, rather than spinning forever.
    if (timedOut) {
      return (
        <div className="flex flex-col items-center justify-center text-center gap-4 py-32">
          <div className="w-16 h-16 rounded-full bg-[#f6c644]/15 flex items-center justify-center">
            <Clock className="text-[#b8881f] dark:text-[#f6c644]" size={32} />
          </div>
          <h1 className="text-title text-beatz-dark-bg dark:text-white">Still waiting for confirmation</h1>
          <p className="text-gray-500 dark:text-gray-300 max-w-md">
            We haven't had confirmation of your payment yet. If you approved the prompt, your
            purchase will appear in your library shortly — you have not been charged twice.
            If the prompt expired, you can try again.
          </p>
          <p className="text-xs font-mono text-gray-400 dark:text-gray-500">Order {order.reference}</p>
          <div className="flex items-center gap-3">
            <Link to="/library" className="h-11 px-6 rounded-full bg-beatz-green text-black font-bold flex items-center">Check my library</Link>
            <Link to="/cart" className="h-11 px-6 rounded-full border border-gray-300 dark:border-white/20 text-beatz-dark-bg dark:text-white font-bold flex items-center">Back to cart</Link>
          </div>
        </div>
      )
    }
    return <AuthorizingState />
  }

  if (order.status === 'failed') {
    return (
      <div className="flex flex-col items-center justify-center text-center gap-4 py-32">
        <div className="w-16 h-16 rounded-full bg-red-500/10 flex items-center justify-center">
          <XCircle className="text-red-500" size={32} />
        </div>
        <h1 className="text-title text-beatz-dark-bg dark:text-white">Payment failed</h1>
        <p className="text-gray-500 dark:text-gray-300 max-w-sm">
          Your payment could not be completed. Nothing was charged for this attempt.
        </p>
        <Link to="/cart" className="h-11 px-6 rounded-full bg-beatz-green text-black font-bold flex items-center">Back to cart</Link>
      </div>
    )
  }

  const itemCount = order.items.reduce((sum, i) => sum + i.quantity, 0)

  return (
    <div className="flex flex-col items-center gap-8 py-12 max-w-2xl mx-auto">
      {/* Success card */}
      <div className="w-full bg-white dark:bg-beatz-dark-surface-2 border border-gray-100 dark:border-transparent rounded-[2rem] p-12 flex flex-col items-center text-center gap-8 shadow-2xl">
        <div className="w-20 h-20 rounded-full bg-beatz-green/10 flex items-center justify-center">
          <div className="w-12 h-12 rounded-full bg-beatz-green flex items-center justify-center">
            <Check size={32} className="text-black" strokeWidth={3} />
          </div>
        </div>

        <div className="flex flex-col gap-2">
          <h1 className="text-4xl font-bold text-beatz-dark-bg dark:text-white tracking-tight">Payment confirmed</h1>
          <p className="text-gray-500 dark:text-gray-300 font-medium">
            {itemCount} item{itemCount > 1 ? 's' : ''} added to your library — yours forever.
          </p>
        </div>

        <div className="flex items-center gap-4 w-full justify-center">
          <Link to="/library" className="h-12 px-8 rounded-full bg-beatz-green text-black font-bold flex items-center justify-center hover:scale-105 transition-transform">
            Go to library
          </Link>
          {/*
            This button had no onClick at all — it styled as enabled, and clicking it did nothing
            and said nothing. On a buy-to-own platform this is the screen where "yours forever" is
            promised, so a dead download control here reads as a broken purchase rather than a
            missing feature.

            "Download all" means one bundled download for the whole order, and that endpoint
            doesn't exist yet — GET /v1/tracks/:id/download now serves a single owned, permitted
            track's lossless file, but nothing bundles a multi-item purchase into one ZIP. Disabled
            and labelled until the bundle endpoint does.
          */}
          <button disabled title="Downloads aren't available yet — your purchase is in your library." className="h-12 px-8 rounded-full border border-gray-200 dark:border-white/10 text-gray-400 dark:text-gray-500 font-bold flex items-center justify-center gap-2 cursor-not-allowed">
            <Download size={18} /> Download all
          </button>
        </div>
      </div>

      {/* Receipt */}
      <div className="w-full flex flex-col gap-6 px-4">
        <div className="flex justify-between items-end border-b border-gray-100 dark:border-white/5 pb-4">
          <div className="flex flex-col gap-1">
            <span className="text-[10px] font-bold text-gray-500 dark:text-gray-300 uppercase tracking-widest">Receipt</span>
            <span className="font-mono text-beatz-dark-bg dark:text-white font-bold">{order.reference}</span>
          </div>
          <div className="flex flex-col items-end gap-1">
            <span className="text-[10px] font-bold text-gray-500 dark:text-gray-300 uppercase tracking-widest">Total paid</span>
            <span className="font-mono text-beatz-green font-bold">{formatPrice({ amount: order.total, currency: 'GHS' })}</span>
          </div>
        </div>

        <div className="flex flex-col gap-4">
          {order.items.map((item) => (
            <div key={item.id} className="flex items-center gap-4">
              <div className="w-12 h-12 rounded overflow-hidden shrink-0">
                <img src={item.image ?? ''} alt={item.title} className="w-full h-full object-cover" />
              </div>
              <div className="flex flex-col flex-1 min-w-0">
                <span className="font-bold text-beatz-dark-bg dark:text-white truncate">{item.title}</span>
                <span className="text-xs text-gray-500 dark:text-gray-300 truncate">{item.quantity > 1 ? `${item.quantity} × ` : ''}{item.subtitle}</span>
              </div>
              <span className="font-mono font-bold text-beatz-dark-bg dark:text-white">{formatPrice({ amount: item.unitPrice.amount * item.quantity, currency: 'GHS' })}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

function AuthorizingState() {
  return (
    <div className="flex flex-col items-center justify-center text-center gap-4 py-32">
      <Loader2 className="animate-spin text-beatz-green" size={40} />
      <h1 className="text-title text-beatz-dark-bg dark:text-white">Authorizing on your phone…</h1>
      <p className="text-gray-500 dark:text-gray-300 max-w-sm">
        Approve the MoMo PIN prompt on your phone to complete this payment.
      </p>
    </div>
  )
}
