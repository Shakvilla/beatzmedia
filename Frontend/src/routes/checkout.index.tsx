import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { CreditCard } from 'lucide-react'
import { useRef, useState } from 'react'
import { cn } from '../utils/cn'
import { useCart } from '../features/cart/cart-context'
import { useToast } from '../components/ui/toast-provider'
import { ApiError } from '../lib/api/errors'
import { formatPrice } from '../lib/format'

export const Route = createFileRoute('/checkout/')({
  component: CheckoutComponent,
})

const PAYMENT_METHODS = [
  { id: 'mtn', name: 'MTN MoMo', subtitle: '0244 ••• 9210 - default', color: 'bg-[#f6c644]', textColor: 'text-black', logoText: 'MTN' },
  { id: 'telecel', name: 'Telecel Cash', subtitle: 'Add new number', color: 'bg-red-600', textColor: 'text-white', logoText: 'T' },
  { id: 'airtel', name: 'AirtelTigo Money', subtitle: 'Add new number', color: 'bg-red-700', textColor: 'text-white', logoText: 'AT' },
  { id: 'card', name: 'Card', subtitle: 'Visa / Mastercard', color: 'bg-beatz-dark-surface-3', textColor: 'text-white', isCard: true },
]

/** UI picker id -> backend Provider wire value (airtel -> airteltigo; others pass through). */
function toProviderWireValue(pickerId: string): string {
  return pickerId === 'airtel' ? 'airteltigo' : pickerId
}

function CheckoutComponent() {
  const [selectedMethod, setSelectedMethod] = useState('mtn')
  const [submitting, setSubmitting] = useState(false)
  const navigate = useNavigate()
  const { toast } = useToast()
  const { items, subtotal, fee, total, checkout } = useCart()
  const idempotencyKeyRef = useRef<string | null>(null)

  if (items.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center text-center gap-4 py-32">
        <h1 className="text-title text-beatz-dark-bg dark:text-white">Nothing to check out</h1>
        <p className="text-gray-500 dark:text-gray-300">Your cart is empty.</p>
        <Link to="/store" className="h-11 px-6 rounded-full bg-beatz-green text-black font-bold flex items-center">Browse the store</Link>
      </div>
    )
  }

  const handlePay = async () => {
    if (submitting) return
    setSubmitting(true)
    if (!idempotencyKeyRef.current) {
      idempotencyKeyRef.current = crypto.randomUUID()
    }
    try {
      const result = await checkout(toProviderWireValue(selectedMethod), idempotencyKeyRef.current)
      navigate({ to: '/checkout/complete', search: { orderId: result.orderId } })
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        toast('Please log in to check out', 'error')
      } else if (e instanceof ApiError && e.status === 429) {
        toast('Too many checkout attempts — please wait a moment and try again', 'error')
      } else if (e instanceof ApiError && e.code === 'CHECKOUT_KIND_UNSUPPORTED') {
        toast("Some items in your cart can't be checked out yet — remove them to continue", 'error')
      } else {
        toast('Payment could not be started — please try again', 'error')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex flex-col gap-8 pb-12">
      <h1 className="text-display tracking-tight text-beatz-dark-bg dark:text-white">Checkout</h1>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 lg:gap-8 items-start">
        {/* Payment */}
        <div className="lg:col-span-2 flex flex-col gap-8">
          <div className="flex flex-col gap-4">
            <h2 className="text-xl font-bold text-beatz-dark-bg dark:text-white">Pay with</h2>
            <div className="flex flex-col gap-3">
              {PAYMENT_METHODS.map((method) => (
                <button
                  key={method.id}
                  onClick={() => setSelectedMethod(method.id)}
                  className={cn(
                    'flex items-center gap-4 p-4 rounded-xl border transition-all text-left group',
                    selectedMethod === method.id
                      ? 'bg-beatz-green/5 border-beatz-green'
                      : 'bg-white dark:bg-beatz-dark-surface-2 border-gray-100 dark:border-white/5 hover:border-gray-300 dark:hover:border-white/20',
                  )}
                >
                  <div className={cn('w-12 h-12 rounded-lg flex items-center justify-center shrink-0 font-bold text-sm', method.color, method.textColor)}>
                    {method.isCard ? <CreditCard size={20} /> : method.logoText}
                  </div>
                  <div className="flex flex-col flex-1">
                    <span className="font-bold text-beatz-dark-bg dark:text-white">{method.name}</span>
                    <span className="text-xs text-gray-500 dark:text-gray-300">{method.subtitle}</span>
                  </div>
                  <div className={cn(
                    'w-6 h-6 rounded-full border-2 flex items-center justify-center transition-colors',
                    selectedMethod === method.id ? 'border-beatz-green bg-beatz-green' : 'border-gray-300 dark:border-white/20',
                  )}>
                    {selectedMethod === method.id && <div className="w-2 h-2 rounded-full bg-black" />}
                  </div>
                </button>
              ))}
            </div>
          </div>

          {selectedMethod === 'mtn' && (
            <div className="p-6 rounded-2xl bg-white dark:bg-beatz-dark-surface-2 border border-gray-100 dark:border-transparent flex flex-col gap-3">
              <h3 className="font-bold text-beatz-dark-bg dark:text-white">Confirm with MoMo PIN</h3>
              <p className="text-sm text-gray-500 dark:text-gray-300 leading-relaxed">
                You'll receive a prompt on <span className="text-beatz-dark-bg dark:text-white font-bold">0244 ••• 9210</span> to authorize this payment. The charge will appear as <span className="text-[#f6c644] font-bold">BEATZCLIK GH</span> on your statement.
              </p>
            </div>
          )}
        </div>

        {/* Order review */}
        <div className="lg:sticky lg:top-24 bg-white dark:bg-beatz-dark-surface-2 border border-gray-100 dark:border-transparent rounded-2xl p-8 flex flex-col gap-6 shadow-xl">
          <h2 className="text-2xl font-bold text-beatz-dark-bg dark:text-white">Your order</h2>

          <div className="flex flex-col gap-4 border-b border-gray-100 dark:border-white/5 pb-6 max-h-64 overflow-y-auto no-scrollbar">
            {items.map((item) => (
              <div key={item.id} className="flex items-center gap-3">
                <div className="w-10 h-10 rounded overflow-hidden shrink-0">
                  <img src={item.image} alt={item.title} className="w-full h-full object-cover" />
                </div>
                <div className="flex flex-col flex-1 min-w-0">
                  <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{item.title}</span>
                  <span className="text-[10px] text-gray-500 dark:text-gray-300 truncate">{item.quantity > 1 ? `${item.quantity} × ` : ''}{item.subtitle}</span>
                </div>
                <span className="text-xs font-mono font-bold text-gray-500 dark:text-gray-300">{formatPrice({ amount: item.price.amount * item.quantity, currency: 'GHS' })}</span>
              </div>
            ))}
          </div>

          <div className="flex flex-col gap-2">
            <div className="flex justify-between text-sm text-gray-500 dark:text-gray-300"><span>Subtotal</span><span className="font-mono">{formatPrice({ amount: subtotal, currency: 'GHS' })}</span></div>
            <div className="flex justify-between text-sm text-gray-500 dark:text-gray-300"><span>Service fee</span><span className="font-mono">{formatPrice({ amount: fee, currency: 'GHS' })}</span></div>
          </div>

          <div className="flex justify-between items-end">
            <span className="text-lg font-bold text-beatz-dark-bg dark:text-white uppercase tracking-tight">Total</span>
            <span className="text-3xl font-mono font-bold text-beatz-green leading-none">{formatPrice({ amount: total, currency: 'GHS' })}</span>
          </div>

          <button
            onClick={handlePay}
            disabled={submitting}
            className="w-full h-14 rounded-full bg-beatz-green text-black font-bold text-lg flex items-center justify-center hover:scale-[1.02] active:scale-[0.98] transition-all shadow-lg shadow-beatz-green/20 disabled:opacity-60"
          >
            {submitting ? 'Processing…' : `Pay ${formatPrice({ amount: total, currency: 'GHS' })} with ${selectedMethod === 'card' ? 'card' : 'MoMo'}`}
          </button>

          <p className="text-center text-[10px] text-gray-500 dark:text-gray-300">By paying, you agree to the Beatzclik Terms.</p>
        </div>
      </div>
    </div>
  )
}
