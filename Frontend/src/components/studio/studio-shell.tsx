import { Link, Outlet, useLocation } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Home, Upload, LineChart, Users, Wallet, Disc3, BadgeCheck, ArrowLeft, Settings, Menu, X, Mic, type LucideIcon } from 'lucide-react'
import { cn } from '../../utils/cn'
import { studioArtist } from '../../lib/studio-data'
import { studioProfileQuery } from '../../lib/api/queries/studio'

/** Two-letter monogram from a display name. */
function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  return (parts[0][0] + (parts[1]?.[0] ?? '')).toUpperCase()
}

const NAV: { to: string; icon: LucideIcon; label: string }[] = [
  { to: '/studio', icon: Home, label: 'Overview' },
  { to: '/studio/releases', icon: Upload, label: 'Releases' },
  { to: '/studio/podcasts', icon: Mic, label: 'Podcasts' },
  { to: '/studio/analytics', icon: LineChart, label: 'Analytics' },
  { to: '/studio/audience', icon: Users, label: 'Audience' },
  { to: '/studio/payouts', icon: Wallet, label: 'Payouts' },
  { to: '/studio/profile', icon: Disc3, label: 'Profile' },
  { to: '/studio/settings', icon: Settings, label: 'Settings' },
]

function Brand({ compact }: { compact?: boolean }) {
  return (
    <div className="flex flex-col gap-1 px-2">
      <span className={cn('font-bold tracking-tight', compact ? 'text-base' : 'text-lg')}>
        <span className="text-beatz-green">Beatzclik</span>
        <span className="text-beatz-dark-bg dark:text-white"> · Artists</span>
      </span>
      {!compact && <span className="text-[10px] font-mono uppercase tracking-[0.2em] text-gray-400 dark:text-gray-500">Studio Dashboard</span>}
    </div>
  )
}

function NavLinks({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <nav className="flex flex-col gap-1">
      {NAV.map(({ to, icon: Icon, label }) => (
        <Link
          key={to}
          to={to}
          onClick={onNavigate}
          activeOptions={{ exact: to === '/studio' }}
          className={cn(
            'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-bold transition-colors',
            'text-gray-500 dark:text-gray-300 hover:text-beatz-dark-bg dark:hover:text-white hover:bg-gray-100 dark:hover:bg-white/5',
            '[&.active]:bg-gray-100 dark:[&.active]:bg-white/10 [&.active]:text-beatz-dark-bg dark:[&.active]:text-white',
          )}
        >
          <Icon size={18} />
          {label}
        </Link>
      ))}
    </nav>
  )
}

function CreatorFooter({ name, avatar, onNavigate }: { name: string; avatar: string | null; onNavigate?: () => void }) {
  return (
    <div className="mt-auto flex flex-col gap-5">
      <Link to="/" onClick={onNavigate} className="flex items-center gap-2 px-2 text-xs font-bold text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white transition-colors">
        <ArrowLeft size={14} /> Back to BeatzClik
      </Link>
      <div className="flex items-center gap-3 px-2 pt-5 border-t border-gray-200 dark:border-white/5">
        <div className="w-9 h-9 rounded-full border border-gray-300 dark:border-white/20 flex items-center justify-center shrink-0 overflow-hidden">
          {avatar ? <img src={avatar} alt={name} className="w-full h-full object-cover" /> : <span className="text-[11px] font-bold text-gray-600 dark:text-gray-300">{initialsOf(name)}</span>}
        </div>
        <div className="flex flex-col min-w-0">
          <span className="text-sm font-bold truncate">{name}</span>
          {studioArtist.verified && (
            <span className="flex items-center gap-1 text-[9px] font-bold uppercase tracking-[0.15em] text-beatz-green"><BadgeCheck size={11} /> Verified</span>
          )}
        </div>
      </div>
    </div>
  )
}

/**
 * Standalone shell for the Artist Studio — desktop sidebar on md+, a mobile
 * top bar + slide-in drawer below that. Mounted by the `/studio` layout route.
 */
export function StudioShell() {
  const { data: profile } = useQuery(studioProfileQuery())
  const name = profile?.displayName.trim() || studioArtist.name
  const [drawerOpen, setDrawerOpen] = useState(false)
  const location = useLocation()

  // Close the mobile drawer whenever the route changes.
  useEffect(() => setDrawerOpen(false), [location.pathname])

  return (
    <div className="flex h-screen bg-beatz-light-bg dark:bg-beatz-dark-bg text-beatz-dark-bg dark:text-white overflow-hidden font-sans">
      {/* Desktop sidebar */}
      <aside className="hidden md:flex w-64 shrink-0 flex-col gap-8 border-r border-gray-200 dark:border-white/5 bg-beatz-light-surface dark:bg-black p-6">
        <Brand />
        <NavLinks />
        <CreatorFooter name={name} avatar={profile?.avatar ?? null} />
      </aside>

      {/* Mobile drawer + backdrop */}
      <div className={cn('md:hidden fixed inset-0 z-50', drawerOpen ? '' : 'pointer-events-none')}>
        <div
          className={cn('absolute inset-0 bg-black/50 backdrop-blur-sm transition-opacity duration-300', drawerOpen ? 'opacity-100' : 'opacity-0')}
          onClick={() => setDrawerOpen(false)}
        />
        <aside
          className={cn(
            'absolute left-0 top-0 h-full w-72 max-w-[82%] flex flex-col gap-8 border-r border-gray-200 dark:border-white/5 bg-beatz-light-surface dark:bg-black p-6 shadow-2xl transition-transform duration-300',
            drawerOpen ? 'translate-x-0' : '-translate-x-full',
          )}
        >
          <div className="flex items-center justify-between">
            <Brand />
            <button onClick={() => setDrawerOpen(false)} aria-label="Close menu" className="w-9 h-9 -mr-1 flex items-center justify-center rounded-full text-gray-500 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-white/10 transition-colors">
              <X size={20} />
            </button>
          </div>
          <NavLinks onNavigate={() => setDrawerOpen(false)} />
          <CreatorFooter name={name} avatar={profile?.avatar ?? null} onNavigate={() => setDrawerOpen(false)} />
        </aside>
      </div>

      {/* Content */}
      <main className="flex-1 overflow-y-auto no-scrollbar">
        {/* Mobile top bar */}
        <header className="md:hidden sticky top-0 z-40 h-14 flex items-center justify-between px-4 bg-beatz-light-bg/90 dark:bg-beatz-dark-bg/90 backdrop-blur-xl border-b border-gray-200 dark:border-white/5">
          <Brand compact />
          <button onClick={() => setDrawerOpen(true)} aria-label="Open menu" className="w-10 h-10 flex items-center justify-center rounded-full text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-white/10 transition-colors">
            <Menu size={22} />
          </button>
        </header>

        <div className="px-4 sm:px-6 md:px-10 lg:px-14 py-6 md:py-10 max-w-7xl mx-auto">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
