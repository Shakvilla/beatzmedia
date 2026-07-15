import { createFileRoute, Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { Check, Download, Loader2, XCircle } from 'lucide-react'
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

function CheckoutCompleteComponent() {
  const { orderId } = Route.useSearch()

  const { data: order, isLoading, isError } = useQuery({
    ...orderQuery(orderId ?? ''),
    enabled: !!orderId,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      return status === 'pending' ? 2000 : false
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
          <button className="h-12 px-8 rounded-full border border-gray-300 dark:border-white/20 text-beatz-dark-bg dark:text-white font-bold flex items-center justify-center gap-2 hover:bg-gray-100 dark:hover:bg-white/10 transition-colors">
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
