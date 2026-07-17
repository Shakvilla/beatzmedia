import { createFileRoute, Link } from '@tanstack/react-router'
import { useState } from 'react'
import { useSuspenseQuery } from '@tanstack/react-query'
import { ArrowLeft, ShoppingCart, Check, Info, Clock, ShieldCheck } from 'lucide-react'
import { storeItemQuery } from '../lib/api/queries/store'
import { formatPrice } from '../lib/format'
import { useStoreCart } from '../features/store/use-store-cart'
import { LicenseTierSelector } from '../features/store/components/license-tier-selector'
import { MerchVariantSelector } from '../features/store/components/merch-variant-selector'
import type { LicenseTier, Money, StoreItem } from '../types'

export const Route = createFileRoute('/store/$itemId')({
  loader: ({ context: { queryClient }, params: { itemId } }) =>
    queryClient.ensureQueryData(storeItemQuery(itemId)),
  component: ProductDetailPage,
  errorComponent: () => <NotFound />,
})

function NotFound() {
  return (
    <div className="flex flex-col items-center justify-center text-center gap-4 py-32">
      <h1 className="text-title text-beatz-dark-bg dark:text-white">Product not found</h1>
      <p className="text-gray-500 dark:text-gray-300">This item may have sold out or been removed.</p>
      <Link to="/store" className="h-11 px-6 rounded-full bg-beatz-green text-black font-bold flex items-center gap-2">
        <ArrowLeft size={16} /> Back to store
      </Link>
    </div>
  )
}

function ProductDetailPage() {
  const { itemId } = Route.useParams()
  const { data: item } = useSuspenseQuery(storeItemQuery(itemId))
  return <ProductDetail item={item} />
}

function ProductDetail({ item }: { item: StoreItem }) {
  const { addToCart } = useStoreCart()

  const [tier, setTier] = useState<LicenseTier>(item.licenseOptions?.[0]?.tier ?? 'LEASE')
  const [variants, setVariants] = useState<Record<string, string>>(
    () => Object.fromEntries((item.variants ?? []).map((v) => [v.label, v.options[0]])),
  )

  const selectedLicense = item.licenseOptions?.find((l) => l.tier === tier)
  const activePrice: Money = selectedLicense?.price ?? item.price

  function handleAdd() {
    if (item.type === 'BEAT_LICENSE' && selectedLicense) {
      addToCart(item, { note: selectedLicense.label, price: selectedLicense.price })
    } else if (item.type === 'MERCH') {
      addToCart(item, { note: Object.values(variants).join(' / ') })
    } else {
      addToCart(item)
    }
  }

  return (
    <div className="flex flex-col gap-10 pb-16 animate-in fade-in slide-in-from-bottom-4 duration-500">
      <Link to="/store" className="flex items-center gap-2 text-sm font-bold text-gray-500 dark:text-gray-300 hover:text-beatz-dark-bg dark:hover:text-white transition-colors w-fit">
        <ArrowLeft size={16} /> Back to store
      </Link>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-12">
        {/* Artwork */}
        <div className="relative aspect-square rounded-3xl overflow-hidden shadow-2xl bg-beatz-light-surface-2 dark:bg-beatz-dark-surface-3">
          <img src={item.image} alt={item.title} className="w-full h-full object-cover" />
          {item.badges && item.badges.length > 0 && (
            <div className="absolute top-4 left-4 flex flex-wrap gap-2">
              {item.badges.map((badge) => (
                <span key={badge} className="text-[10px] font-bold tracking-widest uppercase px-2 py-1 rounded-md bg-black/60 text-white backdrop-blur">
                  {badge}
                </span>
              ))}
            </div>
          )}
        </div>

        {/* Details + purchase */}
        <div className="flex flex-col gap-6">
          <div className="flex flex-col gap-2">
            <span className="text-xs font-bold tracking-[0.3em] uppercase text-beatz-green">{labelForType(item)}</span>
            <h1 className="text-3xl sm:text-4xl lg:text-6xl font-bold text-beatz-dark-bg dark:text-white tracking-tight leading-tight break-words">{item.title}</h1>
            <div className="flex items-center gap-2 text-lg">
              {item.artistId ? (
                <Link to="/artist/$artistId" params={{ artistId: item.artistId }} className="text-beatz-dark-bg dark:text-white hover:underline font-medium">
                  {item.artistName}
                </Link>
              ) : (
                <span className="text-beatz-dark-bg dark:text-white font-medium">{item.artistName}</span>
              )}
            </div>
          </div>

          {item.description && <p className="text-gray-500 dark:text-gray-300 leading-relaxed">{item.description}</p>}

          {/* Type-specific configurator */}
          {item.type === 'BEAT_LICENSE' && item.licenseOptions && (
            <div className="flex flex-col gap-3">
              <span className="text-[10px] font-bold text-gray-500 dark:text-gray-300 uppercase tracking-widest">Choose a license</span>
              <LicenseTierSelector options={item.licenseOptions} value={tier} onChange={setTier} />
            </div>
          )}

          {item.type === 'MERCH' && item.variants && item.variants.length > 0 && (
            <MerchVariantSelector
              variants={item.variants}
              value={variants}
              onChange={(label, option) => setVariants((prev) => ({ ...prev, [label]: option }))}
            />
          )}

          {/* Meta rows */}
          <div className="flex flex-col gap-3 pt-2">
            {item.quality && <MetaRow icon={<Info size={18} />} label="Audio quality" value={item.quality} />}
            {item.dropsAt && (
              <MetaRow icon={<Clock size={18} />} label="Available" value={new Date(item.dropsAt).toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric' })} />
            )}
            {item.stockRemaining != null && (
              <MetaRow icon={<ShieldCheck size={18} />} label="Availability" value={`${item.stockRemaining} remaining`} />
            )}
          </div>

          {/* Purchase bar */}
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mt-2 p-5 rounded-2xl bg-white dark:bg-beatz-dark-surface-2 border border-gray-100 dark:border-transparent shadow-sm">
            <div className="flex flex-col">
              <span className="text-[10px] font-bold text-gray-500 dark:text-gray-300 uppercase tracking-widest">Total</span>
              <span className="text-3xl font-mono font-bold text-beatz-green leading-none">{formatPrice(activePrice)}</span>
            </div>
            <div className="flex items-center gap-3 w-full sm:w-auto">
              <button
                onClick={handleAdd}
                className="h-12 px-6 rounded-full bg-white dark:bg-transparent border border-gray-300 dark:border-white/15 text-beatz-dark-bg dark:text-white font-bold flex items-center justify-center gap-2 whitespace-nowrap flex-1 sm:flex-none hover:bg-gray-100 dark:hover:bg-white/10 transition-colors"
              >
                <ShoppingCart size={18} /> Add to cart
              </button>
              <Link
                to="/checkout"
                onClick={handleAdd}
                className="h-12 px-7 rounded-full bg-beatz-green text-black font-bold flex items-center justify-center gap-2 whitespace-nowrap flex-1 sm:flex-none hover:scale-105 transition-transform"
              >
                Buy now
              </Link>
            </div>
          </div>

          <div className="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-300">
            <Check size={14} className="text-beatz-green" /> Pay with MoMo, card or bank transfer • yours forever
          </div>
        </div>
      </div>
    </div>
  )
}

function MetaRow({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="flex items-center gap-4">
      <div className="w-10 h-10 rounded-xl bg-gray-100 dark:bg-white/5 flex items-center justify-center text-gray-500 dark:text-gray-300">{icon}</div>
      <div className="flex flex-col">
        <span className="text-xs font-bold text-beatz-dark-bg dark:text-white">{label}</span>
        <span className="text-[11px] text-gray-500 dark:text-gray-300">{value}</span>
      </div>
    </div>
  )
}

function labelForType(item: StoreItem): string {
  switch (item.type) {
    case 'BEAT_LICENSE':
      return 'Beat License'
    case 'MERCH':
      return 'Merchandise'
    case 'EXCLUSIVE':
      return 'Exclusive Drop'
    case 'ALBUM':
      return 'Hi-Fi Album'
    default:
      return 'Hi-Fi Track'
  }
}
