import type { EventStatus } from '../../types'
import { cn } from '../../utils/cn'

export const eventMonth = (iso: string) => new Date(iso).toLocaleDateString('en-GB', { month: 'short' }).toUpperCase()
export const eventDay = (iso: string) => new Date(iso).getDate()

export const formatEventDate = (iso: string) =>
  new Date(iso).toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'long', year: 'numeric' })

const STATUS: Record<EventStatus, { label: string; cls: string }> = {
  'on-sale': { label: 'On sale', cls: 'bg-beatz-green/15 text-beatz-green' },
  'selling-fast': { label: 'Selling fast', cls: 'bg-[#f6c644]/15 text-[#f6c644]' },
  'sold-out': { label: 'Sold out', cls: 'bg-beatz-red/15 text-beatz-red' },
}

export function StatusBadge({ status, className }: { status: EventStatus; className?: string }) {
  const s = STATUS[status]
  return (
    <span className={cn('text-[10px] font-bold uppercase tracking-widest px-2 py-1 rounded', s.cls, className)}>
      {s.label}
    </span>
  )
}
