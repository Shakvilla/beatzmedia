import { cn } from '../../utils/cn'

/** Accessible on/off switch used across settings surfaces. */
export function Toggle({ checked, onChange, label }: { checked: boolean; onChange: (v: boolean) => void; label?: string }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      className={cn('w-11 h-6 rounded-full p-0.5 transition-colors shrink-0', checked ? 'bg-beatz-green' : 'bg-gray-300 dark:bg-white/20')}
    >
      <span className={cn('block w-5 h-5 rounded-full bg-white shadow-sm transition-transform', checked ? 'translate-x-5' : 'translate-x-0')} />
    </button>
  )
}
