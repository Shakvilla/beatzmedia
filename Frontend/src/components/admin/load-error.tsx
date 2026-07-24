/** The distinct admin load-error affordance — shown when a query fails, so an outage
 * on a role-gated screen never looks identical to a genuinely empty result. */
export function AdminLoadError({ label, onRetry }: { label: string; onRetry: () => void }) {
  return (
    <div className="py-12 text-center flex flex-col items-center gap-3">
      <p className="text-sm text-gray-400 dark:text-gray-500">{label}</p>
      <button
        onClick={onRetry}
        className="h-9 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-xs font-bold hover:bg-gray-200 dark:hover:bg-white/15 transition-colors"
      >
        Retry
      </button>
    </div>
  )
}
