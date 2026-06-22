import { useState } from "react"
import { Bell, ShoppingCart, Disc3, Settings, LogOut, CheckCheck, ShieldCheck } from "lucide-react"
import { SearchInput } from "../ui/search-input"
import { ThemeToggle } from "../ui/theme-toggle"
import { Link, useNavigate } from "@tanstack/react-router"
import { useCart } from "../../features/cart/cart-context"
import { useAuth, initialsOfAccount } from "../../features/auth/auth-context"
import { useNotifications } from "../../features/notifications/notifications-context"
import { NotificationRow } from "../../features/notifications/notification-row"
import { cn } from "../../utils/cn"

export function Header() {
  const navigate = useNavigate()
  const { count } = useCart()
  const { account, logout } = useAuth()
  const { notifications, unread, markAllRead } = useNotifications()
  const [menuOpen, setMenuOpen] = useState(false)
  const [notifOpen, setNotifOpen] = useState(false)

  return (
    <header className="absolute top-0 right-0 left-0 h-20 flex items-center justify-end px-4 md:px-8 z-20 pointer-events-none">
      <div className="flex items-center gap-3 md:gap-4 pointer-events-auto">
        <div className="hidden sm:block">
          <SearchInput
            className="w-64"
            onChange={(e) => {
              const val = e.target.value
              if (val) navigate({ to: '/search', search: { q: val } })
            }}
          />
        </div>
        <Link
          to="/cart"
          className="relative w-10 h-10 rounded-full bg-white dark:bg-beatz-dark-surface-2 flex items-center justify-center text-gray-600 dark:text-gray-300 hover:scale-105 hover:text-beatz-green transition-all shadow-sm"
          aria-label="Cart"
        >
          <ShoppingCart size={20} />
          {count > 0 && (
            <span className="absolute -top-1 -right-1 min-w-[18px] h-[18px] px-1 rounded-full bg-beatz-green text-black text-[10px] font-bold flex items-center justify-center">
              {count}
            </span>
          )}
        </Link>
        {/* Notifications */}
        <div className="relative">
          <button
            onClick={() => setNotifOpen((o) => !o)}
            aria-label="Notifications"
            className="relative w-10 h-10 rounded-full bg-white dark:bg-beatz-dark-surface-2 flex items-center justify-center text-gray-600 dark:text-gray-300 hover:scale-105 hover:text-beatz-green transition-all shadow-sm"
          >
            <Bell size={20} />
            {unread > 0 && (
              <span className="absolute -top-1 -right-1 min-w-[18px] h-[18px] px-1 rounded-full bg-beatz-green text-black text-[10px] font-bold flex items-center justify-center">
                {unread > 9 ? '9+' : unread}
              </span>
            )}
          </button>

          {notifOpen && (
            <>
              <div className="fixed inset-0 z-40" onClick={() => setNotifOpen(false)} />
              <div className="absolute right-0 top-12 z-50 w-80 max-w-[calc(100vw-2rem)] rounded-2xl bg-white dark:bg-beatz-dark-surface-2 border border-gray-200 dark:border-white/10 shadow-xl overflow-hidden">
                <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100 dark:border-white/5">
                  <span className="text-sm font-bold text-beatz-dark-bg dark:text-white">Notifications</span>
                  {unread > 0 && (
                    <button onClick={markAllRead} className="flex items-center gap-1 text-xs font-bold text-beatz-green hover:underline">
                      <CheckCheck size={13} /> Mark all read
                    </button>
                  )}
                </div>
                <div className="max-h-[60vh] overflow-y-auto no-scrollbar divide-y divide-gray-100 dark:divide-white/5">
                  {notifications.length === 0 ? (
                    <div className="px-4 py-10 text-center text-sm text-gray-400 dark:text-gray-500">You're all caught up.</div>
                  ) : (
                    notifications.slice(0, 6).map((n) => <NotificationRow key={n.id} n={n} onNavigate={() => setNotifOpen(false)} />)
                  )}
                </div>
                <button
                  onClick={() => { setNotifOpen(false); navigate({ to: '/notifications' }) }}
                  className="w-full px-4 py-3 text-sm font-bold text-beatz-green hover:bg-gray-50 dark:hover:bg-white/5 transition-colors border-t border-gray-100 dark:border-white/5"
                >
                  See all
                </button>
              </div>
            </>
          )}
        </div>

        <ThemeToggle />

        {/* Account menu */}
        <div className="relative">
          <button
            onClick={() => setMenuOpen((o) => !o)}
            aria-label="Account"
            className="w-10 h-10 rounded-full overflow-hidden border border-gray-200 dark:border-transparent shadow-sm cursor-pointer hover:scale-105 transition-transform bg-beatz-dark-surface-2 flex items-center justify-center"
          >
            {account?.avatar ? (
              <img src={account.avatar} alt={account.name} className="w-full h-full object-cover" />
            ) : (
              <span className="text-xs font-bold text-white">{initialsOfAccount(account?.name ?? '?')}</span>
            )}
          </button>

          {menuOpen && (
            <>
              <div className="fixed inset-0 z-40" onClick={() => setMenuOpen(false)} />
              <div className="absolute right-0 top-12 z-50 w-56 py-1.5 rounded-2xl bg-white dark:bg-beatz-dark-surface-2 border border-gray-200 dark:border-white/10 shadow-xl">
                <div className="px-4 py-2.5 border-b border-gray-100 dark:border-white/5">
                  <div className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{account?.name}</div>
                  <div className="text-xs text-gray-500 dark:text-gray-400 truncate">{account?.email}</div>
                </div>
                <MenuItem
                  icon={Disc3}
                  label={account?.isArtist ? 'Artist Studio' : 'Become an artist'}
                  onClick={() => { setMenuOpen(false); navigate({ to: '/studio' }) }}
                />
                {account?.isAdmin && (
                  <MenuItem icon={ShieldCheck} label="Admin console" onClick={() => { setMenuOpen(false); navigate({ to: '/admin' }) }} />
                )}
                <MenuItem icon={Settings} label="Settings" onClick={() => { setMenuOpen(false); navigate({ to: '/settings' }) }} />
                <div className="my-1 h-px bg-gray-100 dark:bg-white/5" />
                <MenuItem icon={LogOut} label="Log out" danger onClick={() => { setMenuOpen(false); logout() }} />
              </div>
            </>
          )}
        </div>
      </div>
    </header>
  )
}

function MenuItem({ icon: Icon, label, onClick, danger }: { icon: typeof Disc3; label: string; onClick: () => void; danger?: boolean }) {
  return (
    <button
      onClick={onClick}
      className={cn(
        'w-full flex items-center gap-3 px-4 py-2.5 text-sm font-medium transition-colors',
        danger ? 'text-beatz-red hover:bg-beatz-red/10' : 'text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-white/5',
      )}
    >
      <Icon size={16} /> {label}
    </button>
  )
}
