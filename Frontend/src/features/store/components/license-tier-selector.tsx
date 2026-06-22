import { Check } from 'lucide-react'
import type { LicenseOption, LicenseTier } from '../../../types'
import { formatPrice } from '../../../lib/format'
import { cn } from '../../../utils/cn'

interface LicenseTierSelectorProps {
  options: LicenseOption[]
  value: LicenseTier
  onChange: (tier: LicenseTier) => void
}

export function LicenseTierSelector({ options, value, onChange }: LicenseTierSelectorProps) {
  return (
    <div className="flex flex-col gap-3">
      {options.map((option) => {
        const selected = option.tier === value
        return (
          <button
            key={option.tier}
            onClick={() => onChange(option.tier)}
            className={cn(
              'text-left rounded-2xl border p-5 transition-all',
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
                <div className="flex flex-col">
                  <span className="font-bold text-beatz-dark-bg dark:text-white">{option.label}</span>
                  {option.terms && <span className="text-xs text-gray-500 dark:text-gray-300">{option.terms}</span>}
                </div>
              </div>
              <span className="font-mono font-bold text-beatz-green text-lg">{formatPrice(option.price)}</span>
            </div>

            <ul className="mt-4 grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-2">
              {option.features.map((feature) => (
                <li key={feature} className="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-300">
                  <Check size={14} className="text-beatz-green shrink-0" strokeWidth={2.5} />
                  {feature}
                </li>
              ))}
            </ul>
          </button>
        )
      })}
    </div>
  )
}
