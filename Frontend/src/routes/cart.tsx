import { createFileRoute, Link } from '@tanstack/react-router'
import { Trash2, Minus, Plus, ShoppingCart } from 'lucide-react'
import { useCart } from '../features/cart/cart-context'
import { formatPrice } from '../lib/format'

export const Route = createFileRoute('/cart')({
  component: CartComponent,
})

function CartComponent() {
  const { items, removeItem, setQuantity, subtotal, fee, total, count } = useCart()

  if (items.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center text-center gap-4 py-32">
        <div className="w-16 h-16 rounded-full bg-gray-100 dark:bg-white/5 flex items-center justify-center">
          <ShoppingCart className="text-gray-400" size={28} />
        </div>
        <h1 className="text-title text-beatz-dark-bg dark:text-white">Your cart is empty</h1>
        <p className="text-gray-500 dark:text-gray-300 max-w-sm">Add tracks, beats, merch or tickets and they’ll show up here.</p>
        <Link to="/store" className="mt-2 h-11 px-6 rounded-full bg-beatz-green text-black font-bold flex items-center">
          Browse the store
        </Link>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-8 pb-12">
      <h1 className="text-display tracking-tight text-beatz-dark-bg dark:text-white">Your Cart</h1>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 lg:gap-8 items-start">
        {/* Items */}
        <div className="lg:col-span-2 flex flex-col gap-3">
          {items.map((item) => (
            <div
              key={item.id}
              className="flex items-center gap-4 p-4 rounded-2xl bg-white dark:bg-beatz-dark-surface-2 border border-gray-100 dark:border-transparent group hover:border-beatz-green/40 transition-colors"
            >
              <div className="w-16 h-16 rounded-md overflow-hidden shrink-0">
                <img src={item.image} alt={item.title} className="w-full h-full object-cover" />
              </div>
              <div className="flex flex-col flex-1 min-w-0">
                <span className="font-bold text-beatz-dark-bg dark:text-white truncate">{item.title}</span>
                {item.subtitle && <span className="text-sm text-gray-500 dark:text-gray-300 truncate">{item.subtitle}</span>}
                <span className="text-[10px] font-bold uppercase tracking-widest text-gray-400 mt-0.5">{kindLabel(item.kind)}</span>
              </div>

              {item.stackable && (
                <div className="flex items-center gap-2 shrink-0">
                  <button
                    onClick={() => setQuantity(item.id, item.quantity - 1)}
                    className="w-8 h-8 rounded-full border border-gray-300 dark:border-white/20 flex items-center justify-center text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-white/10 transition-colors"
                    aria-label="Decrease quantity"
                  >
                    <Minus size={14} />
                  </button>
                  <span className="w-6 text-center font-mono font-bold text-beatz-dark-bg dark:text-white">{item.quantity}</span>
                  <button
                    onClick={() => setQuantity(item.id, item.quantity + 1)}
                    className="w-8 h-8 rounded-full border border-gray-300 dark:border-white/20 flex items-center justify-center text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-white/10 transition-colors"
                    aria-label="Increase quantity"
                  >
                    <Plus size={14} />
                  </button>
                </div>
              )}

              <span className="font-mono font-bold text-beatz-green w-20 text-right shrink-0">
                {formatPrice({ amount: item.price.amount * item.quantity, currency: 'GHS' })}
              </span>
              <button
                onClick={() => removeItem(item.id)}
                className="text-gray-400 hover:text-beatz-red transition-colors shrink-0"
                aria-label={`Remove ${item.title}`}
              >
                <Trash2 size={20} />
              </button>
            </div>
          ))}
        </div>

        {/* Summary */}
        <div className="lg:sticky lg:top-24 bg-white dark:bg-beatz-dark-surface-2 border border-gray-100 dark:border-transparent rounded-2xl p-8 flex flex-col gap-6 shadow-xl">
          <h2 className="text-2xl font-bold text-beatz-dark-bg dark:text-white">Order summary</h2>

          <div className="flex flex-col gap-4 border-b border-gray-100 dark:border-white/5 pb-6">
            <div className="flex justify-between items-center text-sm">
              <span className="text-gray-500 dark:text-gray-300">Items ({count})</span>
              <span className="font-mono text-beatz-dark-bg dark:text-white font-bold">{formatPrice({ amount: subtotal, currency: 'GHS' })}</span>
            </div>
            <div className="flex justify-between items-center text-sm">
              <span className="text-gray-500 dark:text-gray-300">Service fee</span>
              <span className="font-mono text-beatz-dark-bg dark:text-white font-bold">{formatPrice({ amount: fee, currency: 'GHS' })}</span>
            </div>
          </div>

          <div className="flex justify-between items-end">
            <span className="text-lg font-bold text-beatz-dark-bg dark:text-white uppercase tracking-tight">Total</span>
            <span className="text-3xl font-mono font-bold text-beatz-green leading-none">{formatPrice({ amount: total, currency: 'GHS' })}</span>
          </div>

          <Link
            to="/checkout"
            className="w-full h-14 rounded-full bg-beatz-green text-black font-bold text-lg flex items-center justify-center hover:scale-[1.02] active:scale-[0.98] transition-all shadow-lg shadow-beatz-green/20"
          >
            Checkout
          </Link>

          <div className="flex items-center justify-center gap-2 text-[10px] font-bold text-gray-500 dark:text-gray-300 uppercase tracking-widest">
            MoMo • Card • Bank transfer
          </div>
        </div>
      </div>
    </div>
  )
}

function kindLabel(kind: string): string {
  switch (kind) {
    case 'track': return 'Track'
    case 'album': return 'Album'
    case 'album-rest': return 'Album (remaining tracks)'
    case 'store': return 'Store item'
    case 'episode': return 'Podcast episode'
    case 'season-pass': return 'Season pass'
    case 'ticket': return 'Event ticket'
    default: return ''
  }
}
