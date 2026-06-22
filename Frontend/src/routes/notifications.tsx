import { createFileRoute } from '@tanstack/react-router'
import { useState } from 'react'
import { Bell, CheckCheck } from 'lucide-react'
import { cn } from '../utils/cn'
import { useNotifications } from '../features/notifications/notifications-context'
import { NotificationRow } from '../features/notifications/notification-row'

export const Route = createFileRoute('/notifications')({
  component: NotificationsPage,
})

const FILTERS = [
  { key: 'all', label: 'All' },
  { key: 'unread', label: 'Unread' },
] as const

function NotificationsPage() {
  const { notifications, unread, markAllRead } = useNotifications()
  const [filter, setFilter] = useState<'all' | 'unread'>('all')

  const list = filter === 'unread' ? notifications.filter((n) => !n.read) : notifications

  return (
    <div className="max-w-2xl mx-auto flex flex-col gap-6">
      <div className="flex items-center justify-between gap-4 flex-wrap">
        <h1 className="text-display text-beatz-dark-bg dark:text-white">Notifications</h1>
        {unread > 0 && (
          <button onClick={markAllRead} className="h-9 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-xs font-bold flex items-center gap-2 hover:bg-gray-200 dark:hover:bg-white/15 transition-colors">
            <CheckCheck size={14} /> Mark all read
          </button>
        )}
      </div>

      <div className="flex items-center gap-1 p-1 rounded-full bg-gray-100 dark:bg-white/10 self-start">
        {FILTERS.map((f) => (
          <button
            key={f.key}
            onClick={() => setFilter(f.key)}
            className={cn('h-8 px-4 rounded-full text-sm font-bold transition-colors flex items-center gap-1.5',
              filter === f.key ? 'bg-white dark:bg-white/15 text-beatz-green shadow-sm' : 'text-gray-500 dark:text-gray-300 hover:text-beatz-dark-bg dark:hover:text-white')}
          >
            {f.label}
            {f.key === 'unread' && unread > 0 && <span className={cn('text-[10px]', filter === f.key ? 'text-beatz-green' : 'text-gray-400')}>{unread}</span>}
          </button>
        ))}
      </div>

      {list.length === 0 ? (
        <div className="flex flex-col items-center justify-center text-center gap-3 py-24 rounded-2xl border border-dashed border-gray-300 dark:border-white/10">
          <div className="w-14 h-14 rounded-full bg-gray-100 dark:bg-white/5 flex items-center justify-center text-gray-400 dark:text-gray-500"><Bell size={24} /></div>
          <p className="text-sm text-gray-500 dark:text-gray-300">{filter === 'unread' ? "You're all caught up." : 'No notifications yet.'}</p>
        </div>
      ) : (
        <div className="rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent shadow-sm dark:shadow-none overflow-hidden divide-y divide-gray-100 dark:divide-white/5">
          {list.map((n) => <NotificationRow key={n.id} n={n} />)}
        </div>
      )}
    </div>
  )
}
