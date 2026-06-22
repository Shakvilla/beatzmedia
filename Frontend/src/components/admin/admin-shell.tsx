import { Link, Outlet, useLocation } from '@tanstack/react-router'
import { useEffect, useState } from 'react'
import { LayoutDashboard, Users, ListMusic, Flag, Wallet, Radio, Activity, ScrollText, SlidersHorizontal, ShieldAlert, LifeBuoy, Scale, Search, ArrowLeft, Menu, X, ShieldCheck, type LucideIcon } from 'lucide-react'
import { cn } from '../../utils/cn'
import { adminUser } from '../../lib/admin-data'
import { AdminCommand } from './admin-command'

const NAV: { to: string; icon: LucideIcon; label: string }[] = [
  { to: '/admin', icon: LayoutDashboard, label: 'Overview' },
  { to: '/admin/users', icon: Users, label: 'Users' },
  { to: '/admin/catalog', icon: ListMusic, label: 'Catalog' },
  { to: '/admin/moderation', icon: Flag, label: 'Moderation' },
  { to: '/admin/finance', icon: Wallet, label: 'Finance' },
  { to: '/admin/editorial', icon: Radio, label: 'Editorial' },
  { to: '/admin/health', icon: Activity, label: 'Health' },
  { to: '/admin/trust', icon: ShieldAlert, label: 'Trust & safety' },
  { to: '/admin/support', icon: LifeBuoy, label: 'Support' },
  { to: '/admin/compliance', icon: Scale, label: 'Compliance' },
  { to: '/admin/audit', icon: ScrollText, label: 'Audit log' },
  { to: '/admin/settings', icon: SlidersHorizontal, label: 'Settings' },
]

function Brand({ compact }: { compact?: boolean }) {
  return (
    <div className="flex flex-col gap-1 px-2">
      <span className={cn('font-bold tracking-tight', compact ? 'text-base' : 'text-lg')}>
        <span className="text-beatz-green">Beatzclik</span>
        <span className="text-beatz-dark-bg dark:text-white"> · Admin</span>
      </span>
      {!compact && <span className="text-[10px] font-mono uppercase tracking-[0.2em] text-gray-400 dark:text-gray-500">Admin Console</span>}
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
          activeOptions={{ exact: to === '/admin' }}
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

function AdminFooter({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <div className="mt-auto flex flex-col gap-5">
      <Link to="/" onClick={onNavigate} className="flex items-center gap-2 px-2 text-xs font-bold text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white transition-colors">
        <ArrowLeft size={14} /> Back to BeatzClik
      </Link>
      <div className="flex items-center gap-3 px-2 pt-5 border-t border-gray-200 dark:border-white/5">
        <div className="w-9 h-9 rounded-full border border-gray-300 dark:border-white/20 flex items-center justify-center shrink-0 text-[11px] font-bold text-gray-600 dark:text-gray-300">
          {adminUser.initials}
        </div>
        <div className="flex flex-col min-w-0">
          <span className="text-sm font-bold truncate">Admin · {adminUser.name}</span>
          <span className="flex items-center gap-1 text-[9px] font-bold uppercase tracking-[0.15em] text-beatz-green">
            <ShieldCheck size={11} /> {adminUser.role}
          </span>
        </div>
      </div>
    </div>
  )
}

/** Standalone shell for the platform admin console. */
export function AdminShell() {
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [cmdOpen, setCmdOpen] = useState(false)
  const location = useLocation()
  useEffect(() => setDrawerOpen(false), [location.pathname])

  // ⌘K / Ctrl+K opens global search.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); setCmdOpen((o) => !o) }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [])

  return (
    <div className="flex h-screen bg-beatz-light-bg dark:bg-beatz-dark-bg text-beatz-dark-bg dark:text-white overflow-hidden font-sans">
      {/* Desktop sidebar */}
      <aside className="hidden md:flex w-64 shrink-0 flex-col gap-6 border-r border-gray-200 dark:border-white/5 bg-beatz-light-surface dark:bg-black p-6">
        <Brand />
        <button onClick={() => setCmdOpen(true)} className="flex items-center gap-2.5 h-9 px-3 rounded-lg bg-gray-100 dark:bg-white/5 text-gray-500 dark:text-gray-400 text-sm font-medium hover:text-beatz-dark-bg dark:hover:text-white transition-colors">
          <Search size={15} /> <span className="flex-1 text-left">Search</span>
          <kbd className="text-[10px] font-mono border border-gray-300 dark:border-white/15 rounded px-1.5 py-0.5">⌘K</kbd>
        </button>
        <NavLinks />
        <AdminFooter />
      </aside>

      {/* Mobile drawer */}
      <div className={cn('md:hidden fixed inset-0 z-50', drawerOpen ? '' : 'pointer-events-none')}>
        <div className={cn('absolute inset-0 bg-black/50 backdrop-blur-sm transition-opacity duration-300', drawerOpen ? 'opacity-100' : 'opacity-0')} onClick={() => setDrawerOpen(false)} />
        <aside className={cn('absolute left-0 top-0 h-full w-72 max-w-[82%] flex flex-col gap-8 border-r border-gray-200 dark:border-white/5 bg-beatz-light-surface dark:bg-black p-6 shadow-2xl transition-transform duration-300', drawerOpen ? 'translate-x-0' : '-translate-x-full')}>
          <div className="flex items-center justify-between">
            <Brand />
            <button onClick={() => setDrawerOpen(false)} aria-label="Close menu" className="w-9 h-9 -mr-1 flex items-center justify-center rounded-full text-gray-500 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-white/10 transition-colors">
              <X size={20} />
            </button>
          </div>
          <NavLinks onNavigate={() => setDrawerOpen(false)} />
          <AdminFooter onNavigate={() => setDrawerOpen(false)} />
        </aside>
      </div>

      {/* Content */}
      <main className="flex-1 overflow-y-auto no-scrollbar">
        <header className="md:hidden sticky top-0 z-40 h-14 flex items-center justify-between px-4 bg-beatz-light-bg/90 dark:bg-beatz-dark-bg/90 backdrop-blur-xl border-b border-gray-200 dark:border-white/5">
          <Brand compact />
          <div className="flex items-center gap-1">
            <button onClick={() => setCmdOpen(true)} aria-label="Search" className="w-10 h-10 flex items-center justify-center rounded-full text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-white/10 transition-colors">
              <Search size={20} />
            </button>
            <button onClick={() => setDrawerOpen(true)} aria-label="Open menu" className="w-10 h-10 flex items-center justify-center rounded-full text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-white/10 transition-colors">
              <Menu size={22} />
            </button>
          </div>
        </header>

        <div className="px-4 sm:px-6 md:px-10 lg:px-14 py-6 md:py-10 max-w-7xl mx-auto">
          <Outlet />
        </div>
      </main>

      <AdminCommand open={cmdOpen} onClose={() => setCmdOpen(false)} sections={NAV} />
    </div>
  )
}
