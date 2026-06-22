import { useNavigate } from '@tanstack/react-router'
import { Tag, Heart, UserPlus, Wallet, Disc3, Bell, type LucideIcon } from 'lucide-react'
import { cn } from '../../utils/cn'
import { useNotifications, type AppNotification, type NotificationType } from './notifications-context'

const ICON: Record<NotificationType, LucideIcon> = {
  sale: Tag,
  tip: Heart,
  follower: UserPlus,
  payout: Wallet,
  release: Disc3,
  system: Bell,
}

export function NotificationRow({ n, onNavigate }: { n: AppNotification; onNavigate?: () => void }) {
  const navigate = useNavigate()
  const { markRead } = useNotifications()
  const Icon = ICON[n.type]

  const open = () => {
    markRead(n.id)
    onNavigate?.()
    if (n.to) navigate({ to: n.to })
  }

  return (
    <button
      onClick={open}
      className={cn(
        'w-full flex items-start gap-3 px-4 py-3 text-left transition-colors hover:bg-gray-50 dark:hover:bg-white/5',
        !n.read && 'bg-beatz-green/[0.06]',
      )}
    >
      <span className={cn('w-9 h-9 rounded-full flex items-center justify-center shrink-0', n.read ? 'bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-300' : 'bg-beatz-green/15 text-beatz-green')}>
        <Icon size={16} />
      </span>
      <div className="flex flex-col flex-1 min-w-0">
        <span className="flex items-center gap-2">
          <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{n.title}</span>
          {!n.read && <span className="w-1.5 h-1.5 rounded-full bg-beatz-green shrink-0" />}
        </span>
        <span className="text-xs text-gray-500 dark:text-gray-300 line-clamp-2">{n.body}</span>
        <span className="text-[10px] font-mono uppercase tracking-wider text-gray-400 dark:text-gray-500 mt-0.5">{n.time}</span>
      </div>
    </button>
  )
}
