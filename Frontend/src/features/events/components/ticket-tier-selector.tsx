import { Check } from 'lucide-react'
import type { TicketTier } from '../../../types'
import { formatPrice } from '../../../lib/format'
import { cn } from '../../../utils/cn'

interface TicketTierSelectorProps {
  tiers: TicketTier[]
  /** Selected tier index. */
  value: number
  onChange: (index: number) => void
}

export function TicketTierSelector({ tiers, value, onChange }: TicketTierSelectorProps) {
  return (
    <div className="flex flex-col gap-3">
      {tiers.map((tier, index) => {
        const selected = index === value && !tier.soldOut
        return (
          <button
            key={tier.name}
            disabled={tier.soldOut}
            onClick={() => onChange(index)}
            className={cn(
              'text-left rounded-2xl border p-5 transition-all disabled:opacity-50 disabled:cursor-not-allowed',
              selected
                ? 'border-beatz-green bg-beatz-green/5 ring-1 ring-beatz-green/40'
                : 'border-gray-200 dark:border-transparent bg-white dark:bg-beatz-dark-surface-2 hover:border-gray-300 dark:hover:border-transparent',
            )}
          >
            <div className="flex items-center justify-between gap-4">
              <div className="flex items-center gap-3">
                <div
                  className={cn(
                    'w-5 h-5 rounded-full border-2 flex items-center justify-center shrink-0 transition-colors',
                    selected ? 'border-beatz-green bg-beatz-green' : 'border-gray-300 dark:border-white/30',
                  )}
                >
                  {selected && <Check size={12} className="text-black" strokeWidth={3} />}
                </div>
                <span className="font-bold text-beatz-dark-bg dark:text-white">
                  {tier.name}
                  {tier.soldOut && <span className="ml-2 text-[10px] font-bold uppercase tracking-widest text-beatz-red">Sold out</span>}
                </span>
              </div>
              <span className="font-mono font-bold text-beatz-green text-lg">{formatPrice(tier.price)}</span>
            </div>

            {tier.perks && tier.perks.length > 0 && (
              <ul className="mt-3 flex flex-wrap gap-x-6 gap-y-2 pl-8">
                {tier.perks.map((perk) => (
                  <li key={perk} className="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-300">
                    <Check size={14} className="text-beatz-green shrink-0" strokeWidth={2.5} />
                    {perk}
                  </li>
                ))}
              </ul>
            )}
          </button>
        )
      })}
    </div>
  )
}
