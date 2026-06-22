import type { LucideIcon } from 'lucide-react'

/**
 * Lightweight stand-in for studio sections whose wireframes are still pending.
 * Keeps the shell navigable while each screen is designed.
 */
export function SectionPlaceholder({
  icon: Icon,
  title,
  description,
}: {
  icon: LucideIcon
  title: string
  description: string
}) {
  return (
    <div className="flex flex-col gap-10">
      <h1 className="text-display text-beatz-dark-bg dark:text-white">{title}</h1>
      <div className="flex flex-col items-center justify-center text-center gap-4 py-24 rounded-2xl border border-dashed border-gray-300 dark:border-white/10">
        <div className="w-14 h-14 rounded-full bg-gray-100 dark:bg-white/5 flex items-center justify-center text-gray-400 dark:text-gray-500">
          <Icon size={26} />
        </div>
        <p className="text-sm text-gray-500 dark:text-gray-300 max-w-sm">{description}</p>
        <span className="text-[10px] font-mono uppercase tracking-[0.2em] text-gray-400 dark:text-gray-600">
          Design in progress
        </span>
      </div>
    </div>
  )
}
