import { createFileRoute } from '@tanstack/react-router'
import { useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Search, Send, Check, UserPlus } from 'lucide-react'
import { cn } from '../utils/cn'
import { useToast } from '../components/ui/toast-provider'
import type { TicketStatus, TicketPriority } from '../lib/admin-data'
import { supportTicketsQuery, apiReplyToTicket, apiAssignTicket, apiResolveTicket } from '../lib/api/queries/admin-support'
import { useAuth } from '../features/auth/auth-context'

export const Route = createFileRoute('/admin/support')({
  component: AdminSupport,
})

const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent shadow-sm dark:shadow-none'

const FILTERS: { key: TicketStatus | 'all'; label: string }[] = [
  { key: 'open', label: 'Open' },
  { key: 'pending', label: 'Pending' },
  { key: 'resolved', label: 'Resolved' },
  { key: 'all', label: 'All' },
]

function AdminSupport() {
  const { toast } = useToast()
  const queryClient = useQueryClient()
  const { account } = useAuth()
  const { data: tickets = [] } = useQuery(supportTicketsQuery())
  const [filter, setFilter] = useState<TicketStatus | 'all'>('open')
  const [query, setQuery] = useState('')
  const [activeId, setActiveId] = useState<string>('')
  const [reply, setReply] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const q = query.trim().toLowerCase()
  const list = useMemo(
    () => tickets.filter((t) => (filter === 'all' || t.status === filter) && (!q || `${t.subject} ${t.requester}`.toLowerCase().includes(q))),
    [tickets, filter, q],
  )
  const active = tickets.find((t) => t.id === activeId) ?? list[0]

  const invalidate = () => queryClient.invalidateQueries({ queryKey: supportTicketsQuery().queryKey })

  const send = async () => {
    if (!reply.trim() || !active || submitting) return
    setActiveId(active.id)
    setSubmitting(true)
    try {
      await apiReplyToTicket(active.id, reply.trim())
      setReply('')
      await invalidate()
      toast('Reply sent', 'success')
    } catch (e) { toast(e instanceof Error ? e.message : 'Could not send reply', 'error') }
    finally { setSubmitting(false) }
  }
  const assign = async () => {
    if (!active || !account) return
    setActiveId(active.id)
    try { await apiAssignTicket(active.id, account.id); await invalidate(); toast('Assigned to you', 'success') }
    catch (e) { toast(e instanceof Error ? e.message : 'Could not assign', 'error') }
  }
  const resolve = async () => {
    if (!active || submitting) return
    setActiveId(active.id)
    setSubmitting(true)
    try { await apiResolveTicket(active.id); await invalidate(); toast('Ticket resolved', 'success') }
    catch (e) { toast(e instanceof Error ? e.message : 'Could not resolve', 'error') }
    finally { setSubmitting(false) }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h1 className="text-display text-beatz-dark-bg dark:text-white">Support inbox</h1>
        <span className="text-sm text-gray-500 dark:text-gray-300">Fan and artist support across email and in-app</span>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[340px_1fr] gap-4 items-start">
        {/* Ticket list */}
        <div className="flex flex-col gap-3">
          <div className="relative">
            <Search size={15} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-400" />
            <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search tickets"
              className="w-full h-10 pl-10 pr-3 rounded-full bg-white dark:bg-white/5 border border-gray-200 dark:border-white/10 text-sm text-beatz-dark-bg dark:text-white placeholder:text-gray-400 focus:outline-none focus:border-beatz-green/60" />
          </div>
          <div className="flex items-center gap-1 p-1 rounded-full bg-gray-100 dark:bg-white/10 self-start">
            {FILTERS.map((f) => (
              <button key={f.key} onClick={() => setFilter(f.key)}
                className={cn('h-8 px-3 rounded-full text-xs font-bold transition-colors', filter === f.key ? 'bg-white dark:bg-white/15 text-beatz-green shadow-sm' : 'text-gray-500 dark:text-gray-300 hover:text-beatz-dark-bg dark:hover:text-white')}>
                {f.label}
              </button>
            ))}
          </div>
          <div className={cn(CARD, 'flex flex-col overflow-hidden')}>
            {list.length === 0 ? (
              <div className="py-10 text-center text-sm text-gray-400 dark:text-gray-500">No tickets.</div>
            ) : list.map((t) => (
              <button key={t.id} onClick={() => setActiveId(t.id)}
                className={cn('flex flex-col gap-1 px-4 py-3 text-left border-b border-gray-100 dark:border-white/5 last:border-0 transition-colors', active?.id === t.id ? 'bg-beatz-green/[0.07]' : 'hover:bg-gray-50 dark:hover:bg-white/5')}>
                <div className="flex items-center gap-2">
                  <PriorityDot priority={t.priority} />
                  <span className="flex-1 text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{t.subject}</span>
                  <StatusPill status={t.status} />
                </div>
                <span className="text-xs text-gray-500 dark:text-gray-400 truncate">{t.requester} · {t.channel} · {t.age}</span>
              </button>
            ))}
          </div>
        </div>

        {/* Conversation */}
        <section className={cn(CARD, 'flex flex-col min-h-[420px]')}>
          {!active ? (
            <div className="flex-1 flex items-center justify-center text-sm text-gray-400 dark:text-gray-500">Select a ticket.</div>
          ) : (
            <>
              <div className="flex items-center justify-between gap-3 p-5 border-b border-gray-100 dark:border-white/5 flex-wrap">
                <div className="flex flex-col gap-0.5 min-w-0">
                  <span className="flex items-center gap-2"><h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white truncate">{active.subject}</h2><StatusPill status={active.status} /></span>
                  <span className="text-xs text-gray-500 dark:text-gray-400">{active.requester} · {active.channel}</span>
                </div>
                <div className="flex items-center gap-2">
                  <button onClick={assign} className="h-9 px-3.5 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-xs font-bold flex items-center gap-1.5 hover:bg-gray-200 dark:hover:bg-white/15 transition-colors"><UserPlus size={14} /> Assign</button>
                  {active.status !== 'resolved' && <button onClick={resolve} disabled={submitting} className="h-9 px-3.5 rounded-full bg-beatz-green/10 text-beatz-green text-xs font-bold flex items-center gap-1.5 hover:bg-beatz-green/20 transition-colors"><Check size={14} /> Resolve</button>}
                </div>
              </div>

              <div className="flex-1 flex flex-col gap-3 p-5 overflow-y-auto no-scrollbar">
                {active.messages.map((m) => (
                  <div key={m.id} className={cn('flex flex-col gap-1 max-w-[80%]', m.from === 'agent' ? 'self-end items-end' : 'self-start items-start')}>
                    <div className={cn('px-3.5 py-2.5 rounded-2xl text-sm', m.from === 'agent' ? 'bg-beatz-green text-black rounded-br-sm' : 'bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white rounded-bl-sm')}>{m.text}</div>
                    <span className="text-[10px] font-mono uppercase tracking-wider text-gray-400 dark:text-gray-500">{m.author} · {m.time}</span>
                  </div>
                ))}
              </div>

              <div className="p-4 border-t border-gray-100 dark:border-white/5 flex items-end gap-2">
                <textarea value={reply} onChange={(e) => setReply(e.target.value)} rows={1} placeholder="Write a reply…"
                  onKeyDown={(e) => { if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) send() }}
                  className="flex-1 max-h-28 rounded-xl bg-white dark:bg-white/5 border border-gray-200 dark:border-white/10 px-4 py-2.5 text-sm text-beatz-dark-bg dark:text-white placeholder:text-gray-400 focus:outline-none focus:border-beatz-green/60 resize-none" />
                <button onClick={send} disabled={!reply.trim() || submitting} aria-label="Send" className="w-11 h-11 rounded-full bg-beatz-green text-black flex items-center justify-center shrink-0 hover:scale-105 transition-transform disabled:opacity-40 disabled:hover:scale-100"><Send size={18} /></button>
              </div>
            </>
          )}
        </section>
      </div>
    </div>
  )
}

function PriorityDot({ priority }: { priority: TicketPriority }) {
  const cls = priority === 'high' ? 'bg-beatz-red' : priority === 'normal' ? 'bg-[#f6c644]' : 'bg-gray-400 dark:bg-white/30'
  return <span className={cn('w-2 h-2 rounded-full shrink-0', cls)} title={priority} />
}

function StatusPill({ status }: { status: TicketStatus }) {
  const cls = status === 'open' ? 'bg-beatz-green/15 text-beatz-green' : status === 'pending' ? 'bg-[#f6c644]/20 text-[#b8881f] dark:text-[#f6c644]' : 'bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-300'
  return <span className={cn('px-2 py-0.5 rounded-full text-[10px] font-bold shrink-0', cls)}>{status}</span>
}
