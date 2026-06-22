interface StoreTabHeadingProps {
  title: string
  subtitle?: string
  count: number
}

/** Editorial heading for a store catalog tab: title, blurb and a result count. */
export function StoreTabHeading({ title, subtitle, count }: StoreTabHeadingProps) {
  return (
    <div className="flex items-end justify-between gap-4 border-b border-gray-200 dark:border-white/5 pb-3">
      <div className="flex flex-col gap-1">
        <h2 className="text-title text-beatz-dark-bg dark:text-white">{title}</h2>
        {subtitle && <p className="text-sm text-gray-500 dark:text-gray-300">{subtitle}</p>}
      </div>
      <span className="shrink-0 text-xs font-mono uppercase tracking-widest text-gray-500 dark:text-gray-300">
        {count} {count === 1 ? 'item' : 'items'}
      </span>
    </div>
  )
}
