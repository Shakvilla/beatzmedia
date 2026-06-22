import type { MerchVariant } from '../../../types'
import { cn } from '../../../utils/cn'

interface MerchVariantSelectorProps {
  variants: MerchVariant[]
  /** Selected option per variant label, e.g. { Size: 'M', Colour: 'Black' }. */
  value: Record<string, string>
  onChange: (label: string, option: string) => void
}

export function MerchVariantSelector({ variants, value, onChange }: MerchVariantSelectorProps) {
  return (
    <div className="flex flex-col gap-6">
      {variants.map((variant) => (
        <div key={variant.label} className="flex flex-col gap-3">
          <span className="text-[10px] font-bold text-gray-500 dark:text-gray-300 uppercase tracking-widest">{variant.label}</span>
          <div className="flex flex-wrap gap-2">
            {variant.options.map((option) => {
              const selected = value[variant.label] === option
              return (
                <button
                  key={option}
                  onClick={() => onChange(variant.label, option)}
                  className={cn(
                    'min-w-12 h-10 px-4 rounded-full border text-sm font-bold transition-all',
                    selected
                      ? 'border-beatz-green bg-beatz-green text-black'
                      : 'border-gray-300 dark:border-white/15 text-beatz-dark-bg dark:text-white hover:border-gray-400 dark:hover:border-white/40',
                  )}
                >
                  {option}
                </button>
              )
            })}
          </div>
        </div>
      ))}
    </div>
  )
}
