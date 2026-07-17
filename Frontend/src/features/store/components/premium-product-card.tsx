import { Link } from '@tanstack/react-router'
import { Plus } from 'lucide-react'
import type { StoreItem } from '../../../types'
import { formatPrice } from '../../../lib/format'
import { cn } from '../../../utils/cn'

/** Lowest price across a beat's license tiers (for "from ₵X" display). */
function lowestLicensePrice(item: StoreItem): number {
  if (!item.licenseOptions?.length) return item.price.amount
  return Math.min(...item.licenseOptions.map((l) => l.price.amount))
}

interface PremiumProductCardProps {
  item: StoreItem
  onAdd?: (item: StoreItem) => void
  className?: string
}

const BADGE_STYLES: Record<string, string> = {
  'HI-FI LOSSLESS': 'bg-beatz-blue text-white',
  'STEMS INCLUDED': 'bg-beatz-green text-black',
  LIMITED: 'bg-beatz-red text-white',
  'EARLY ACCESS': 'bg-[#f6c644] text-black',
  VIP: 'bg-purple-600 text-white',
  OFFICIAL: 'bg-black/55 text-white backdrop-blur',
}

export function PremiumProductCard({ item, onAdd, className }: PremiumProductCardProps) {
  const isBeat = item.type === 'BEAT_LICENSE'
  const priceLabel = isBeat
    ? `from ${formatPrice({ amount: lowestLicensePrice(item), currency: 'GHS' })}`
    : formatPrice(item.price)

  return (
    <Link
      to="/store/$itemId"
      params={{ itemId: item.id }}
      className={cn('group flex flex-col gap-3.5 cursor-pointer', className)}
    >
      <div className="relative aspect-square overflow-hidden rounded-xl bg-beatz-light-surface-2 dark:bg-beatz-dark-surface-3 ring-1 ring-black/5 dark:ring-white/5 shadow-sm transition-all duration-300 group-hover:shadow-2xl group-hover:ring-black/10 dark:group-hover:ring-white/10">
        <img
          src={item.image}
          alt={item.title}
          className="w-full h-full object-cover transition-transform duration-[600ms] ease-out group-hover:scale-[1.04]"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-black/25 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300" />

        {/* Badges */}
        {item.badges && item.badges.length > 0 && (
          <div className="absolute top-3 left-3 flex flex-wrap gap-1.5">
            {item.badges.map((badge) => (
              <span
                key={badge}
                className={cn(
                  'text-[9px] font-bold tracking-[0.12em] uppercase px-1.5 py-0.5 rounded',
                  BADGE_STYLES[badge] ?? 'bg-black/55 text-white backdrop-blur',
                )}
              >
                {badge}
              </span>
            ))}
          </div>
        )}

        {/* Quick add */}
        {onAdd && (
          <button
            onClick={(e) => {
              e.preventDefault()
              e.stopPropagation()
              onAdd(item)
            }}
            aria-label={`Add ${item.title} to cart`}
            className="absolute right-3 bottom-3 z-20 flex items-center justify-center w-10 h-10 rounded-full bg-beatz-green shadow-lg shadow-black/20 transition-all duration-300 opacity-0 translate-y-1 group-hover:opacity-100 group-hover:translate-y-0 hover:scale-110"
          >
            <Plus size={20} className="text-black" strokeWidth={2.5} />
          </button>
        )}
      </div>

      <div className="flex flex-col gap-0.5">
        <h3 className="font-bold text-[15px] text-beatz-dark-bg dark:text-white truncate group-hover:text-beatz-green transition-colors">
          {item.title}
        </h3>
        <span className="text-sm text-beatz-dark-surface-3 dark:text-beatz-light-surface-3 truncate">
          {item.artistName}
        </span>
        <div className="flex items-center justify-between mt-1.5">
          <span className="font-mono font-bold text-beatz-green text-sm">{priceLabel}</span>
          {item.stockRemaining != null && item.stockRemaining <= 25 && (
            <span className="text-[10px] font-bold uppercase tracking-widest text-beatz-red">
              {item.stockRemaining} left
            </span>
          )}
        </div>
      </div>
    </Link>
  )
}
