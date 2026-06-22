import { Link } from '@tanstack/react-router'
import { Home, Search, Store, Mic, Library, type LucideIcon } from 'lucide-react'

const ITEMS: { to: string; icon: LucideIcon; label: string; exact?: boolean }[] = [
  { to: '/', icon: Home, label: 'Home', exact: true },
  { to: '/search', icon: Search, label: 'Search' },
  { to: '/store', icon: Store, label: 'Store' },
  { to: '/podcasts', icon: Mic, label: 'Podcasts' },
  { to: '/library', icon: Library, label: 'Library' },
]

/** Native-app-style bottom tab bar, shown only on mobile (below md). */
export function MobileNav() {
  return (
    <nav className="md:hidden fixed bottom-0 left-0 right-0 z-50 h-16 bg-beatz-light-surface/95 dark:bg-black/95 backdrop-blur-xl border-t border-gray-200 dark:border-white/5 flex items-stretch justify-around px-1">
      {ITEMS.map(({ to, icon: Icon, label, exact }) => (
        <Link
          key={to}
          to={to}
          activeOptions={{ exact: Boolean(exact) }}
          className="flex flex-col items-center justify-center gap-1 flex-1 text-gray-500 dark:text-gray-300 hover:text-beatz-dark-bg dark:hover:text-white [&.active]:text-beatz-green transition-colors"
        >
          <Icon size={22} />
          <span className="text-[10px] font-bold">{label}</span>
        </Link>
      ))}
    </nav>
  )
}
