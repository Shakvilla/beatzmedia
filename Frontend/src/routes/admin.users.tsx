import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useMemo, useState } from 'react'
import { Search, Download, MoreHorizontal, BadgeCheck, Ban, RotateCcw, Eye, Check } from 'lucide-react'
import { cn } from '../utils/cn'
import { useToast } from '../components/ui/toast-provider'
import { getAdminUsers, USER_COUNTS, type AdminUserRow, type UserStatus } from '../lib/admin-data'
import { usePaged, Pagination } from '../components/admin/pagination'

export const Route = createFileRoute('/admin/users')({
  component: AdminUsers,
})

const CARD = 'rounded-2xl bg-white dark:bg-beatz-dark-surface border border-gray-200 dark:border-transparent shadow-sm dark:shadow-none'

type FilterKey = 'all' | 'fans' | 'artists' | 'verified' | 'suspended'
const FILTERS: { key: FilterKey; label: string; count: number }[] = [
  { key: 'all', label: 'All', count: USER_COUNTS.all },
  { key: 'fans', label: 'Fans', count: USER_COUNTS.fans },
  { key: 'artists', label: 'Artists', count: USER_COUNTS.artists },
  { key: 'verified', label: 'Verified', count: USER_COUNTS.verified },
  { key: 'suspended', label: 'Suspended', count: USER_COUNTS.suspended },
]

const matchesFilter = (u: AdminUserRow, f: FilterKey) =>
  f === 'all' ? true : f === 'fans' ? u.role === 'fan' : f === 'artists' ? u.role === 'artist' : f === 'verified' ? u.verified : u.status === 'suspended'

function AdminUsers() {
  const { toast } = useToast()
  const navigate = useNavigate()
  const [users, setUsers] = useState<AdminUserRow[]>(() => getAdminUsers())
  const [filter, setFilter] = useState<FilterKey>('all')
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<Set<string>>(new Set())

  const q = query.trim().toLowerCase()
  const rows = useMemo(
    () => users.filter((u) => matchesFilter(u, filter) && (!q || `${u.name} ${u.email}`.toLowerCase().includes(q))),
    [users, filter, q],
  )
  const paged = usePaged(rows)

  const setStatus = (id: string, status: UserStatus) => setUsers((list) => list.map((u) => (u.id === id ? { ...u, status } : u)))
  const verify = (id: string) => setUsers((list) => list.map((u) => (u.id === id ? { ...u, verified: true } : u)))

  const allShownSelected = rows.length > 0 && rows.every((u) => selected.has(u.id))
  const toggleAll = () => setSelected(allShownSelected ? new Set() : new Set(rows.map((u) => u.id)))
  const toggleOne = (id: string) => setSelected((s) => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n })

  const bulkSuspend = () => {
    setUsers((list) => list.map((u) => (selected.has(u.id) ? { ...u, status: 'suspended' } : u)))
    toast(`${selected.size} user${selected.size > 1 ? 's' : ''} suspended`, 'success')
    setSelected(new Set())
  }

  return (
    <div className="flex flex-col gap-6">
      {/* Header */}
      <div className="flex items-center justify-between gap-4 flex-wrap">
        <h1 className="text-display text-beatz-dark-bg dark:text-white">Users</h1>
        <div className="flex items-center gap-3">
          <div className="relative w-56 sm:w-72">
            <Search size={15} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-400" />
            <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search by name, email, ID…"
              className="w-full h-11 pl-10 pr-3 rounded-full bg-white dark:bg-white/5 border border-gray-200 dark:border-white/10 text-sm text-beatz-dark-bg dark:text-white placeholder:text-gray-400 focus:outline-none focus:border-beatz-green/60" />
          </div>
          <button onClick={() => toast('Exporting users as CSV', 'success')} className="h-11 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-sm font-bold flex items-center gap-2 hover:bg-gray-200 dark:hover:bg-white/15 transition-colors">
            <Download size={16} /> Export
          </button>
        </div>
      </div>

      {/* Filter chips */}
      <div className="flex items-center gap-2 flex-wrap">
        {FILTERS.map((f) => (
          <button key={f.key} onClick={() => setFilter(f.key)}
            className={cn('h-9 px-4 rounded-full text-sm font-bold transition-colors',
              filter === f.key ? 'bg-beatz-green/15 text-beatz-green' : 'bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-white/15')}>
            {f.label} · {f.count.toLocaleString()}
          </button>
        ))}
      </div>

      {/* Bulk bar */}
      {selected.size > 0 && (
        <div className="flex items-center justify-between gap-4 px-4 py-3 rounded-xl bg-beatz-green/10 border border-beatz-green/20">
          <span className="text-sm font-bold text-beatz-dark-bg dark:text-white">{selected.size} selected</span>
          <div className="flex items-center gap-2">
            <button onClick={bulkSuspend} className="h-9 px-4 rounded-full bg-beatz-red text-white text-xs font-bold hover:bg-beatz-red-light transition-colors">Suspend</button>
            <button onClick={() => setSelected(new Set())} className="h-9 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-xs font-bold hover:bg-gray-200 dark:hover:bg-white/15 transition-colors">Clear</button>
          </div>
        </div>
      )}

      {/* Table */}
      <section className={cn(CARD, 'p-2 sm:p-4')}>
        <div className="overflow-x-auto">
          <div className="min-w-[820px]">
            <div className="flex items-center gap-4 px-3 pb-2 border-b border-gray-200 dark:border-white/10 text-[10px] font-bold uppercase tracking-widest text-gray-400 dark:text-gray-500">
              <span className="w-5 shrink-0"><Checkbox checked={allShownSelected} onChange={toggleAll} /></span>
              <span className="flex-1">User</span>
              <span className="w-40 shrink-0">Email</span>
              <span className="w-28 shrink-0">Role</span>
              <span className="w-20 shrink-0">Joined</span>
              <span className="w-24 shrink-0">Last active</span>
              <span className="w-24 shrink-0">Status</span>
              <span className="w-8 shrink-0" />
            </div>

            {rows.length === 0 ? (
              <div className="py-12 text-center text-sm text-gray-400 dark:text-gray-500">No matching users.</div>
            ) : (
              paged.pageItems.map((u) => (
                <UserRow key={u.id} user={u} selected={selected.has(u.id)} onSelect={() => toggleOne(u.id)}
                  onView={() => navigate({ to: '/admin/users/$userId', params: { userId: u.id } })}
                  onVerify={() => { verify(u.id); toast(`${u.name} verified`, 'success') }}
                  onSuspend={() => { setStatus(u.id, 'suspended'); toast(`${u.name} suspended`, 'success') }}
                  onReactivate={() => { setStatus(u.id, 'active'); toast(`${u.name} reactivated`, 'success') }}
                />
              ))
            )}
          </div>
        </div>
        <Pagination paged={paged} />
      </section>
    </div>
  )
}

function Checkbox({ checked, onChange }: { checked: boolean; onChange: () => void }) {
  return (
    <button onClick={onChange} role="checkbox" aria-checked={checked}
      className={cn('w-5 h-5 rounded-md border flex items-center justify-center transition-colors shrink-0',
        checked ? 'bg-beatz-green border-beatz-green' : 'border-gray-300 dark:border-white/25 hover:border-beatz-green')}>
      {checked && <Check size={13} strokeWidth={3} className="text-black" />}
    </button>
  )
}

function RolePill({ user }: { user: AdminUserRow }) {
  if (user.role === 'artist' && user.verified) {
    return <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-bold bg-beatz-green/15 text-beatz-green">Artist <BadgeCheck size={12} /></span>
  }
  return <span className="px-2.5 py-1 rounded-full text-xs font-bold bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300 capitalize">{user.role}</span>
}

function StatusPill({ status }: { status: UserStatus }) {
  const cls = status === 'pending'
    ? 'bg-[#f6c644]/20 text-[#b8881f] dark:text-[#f6c644]'
    : status === 'suspended'
      ? 'bg-beatz-red/15 text-beatz-red'
      : 'bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-300'
  return <span className={cn('px-2.5 py-1 rounded-full text-[10px] font-bold', cls)}>{status}</span>
}

function UserRow({ user: u, selected, onSelect, onView, onVerify, onSuspend, onReactivate }: {
  user: AdminUserRow; selected: boolean; onSelect: () => void
  onView: () => void; onVerify: () => void; onSuspend: () => void; onReactivate: () => void
}) {
  const [menuOpen, setMenuOpen] = useState(false)
  return (
    <div className={cn('flex items-center gap-4 px-3 py-3 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0 transition-colors', selected ? 'bg-beatz-green/[0.05]' : 'hover:bg-gray-50 dark:hover:bg-white/5')}>
      <span className="w-5 shrink-0"><Checkbox checked={selected} onChange={onSelect} /></span>
      <div className="flex items-center gap-3 flex-1 min-w-0">
        <div className="w-9 h-9 rounded-full border border-gray-300 dark:border-white/20 flex items-center justify-center shrink-0 text-xs font-bold text-gray-600 dark:text-gray-300">{u.initial}</div>
        <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{u.name}</span>
      </div>
      <span className="w-40 shrink-0 text-sm text-gray-500 dark:text-gray-300 truncate">{u.email}</span>
      <span className="w-28 shrink-0"><RolePill user={u} /></span>
      <span className="w-20 shrink-0 text-sm text-gray-500 dark:text-gray-300">{u.joined}</span>
      <span className="w-24 shrink-0 text-sm text-gray-500 dark:text-gray-300">{u.lastActive}</span>
      <span className="w-24 shrink-0"><StatusPill status={u.status} /></span>
      <div className="w-8 shrink-0 relative flex justify-end">
        <button onClick={() => setMenuOpen((o) => !o)} aria-label="User actions" className="w-8 h-8 flex items-center justify-center rounded-full text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white hover:bg-gray-100 dark:hover:bg-white/10 transition-colors">
          <MoreHorizontal size={18} />
        </button>
        {menuOpen && (
          <>
            <div className="fixed inset-0 z-40" onClick={() => setMenuOpen(false)} />
            <div className="absolute right-0 top-9 z-50 w-44 py-1 rounded-xl bg-white dark:bg-beatz-dark-surface-2 border border-gray-200 dark:border-white/10 shadow-xl">
              <MenuItem icon={Eye} label="View profile" onClick={() => { onView(); setMenuOpen(false) }} />
              {u.role === 'artist' && !u.verified && <MenuItem icon={BadgeCheck} label="Verify artist" onClick={() => { onVerify(); setMenuOpen(false) }} />}
              {u.status === 'suspended'
                ? <MenuItem icon={RotateCcw} label="Reactivate" onClick={() => { onReactivate(); setMenuOpen(false) }} />
                : <MenuItem icon={Ban} label="Suspend" danger onClick={() => { onSuspend(); setMenuOpen(false) }} />}
            </div>
          </>
        )}
      </div>
    </div>
  )
}

function MenuItem({ icon: Icon, label, onClick, danger }: { icon: typeof Eye; label: string; onClick: () => void; danger?: boolean }) {
  return (
    <button onClick={onClick}
      className={cn('w-full flex items-center gap-2.5 px-3 py-2 text-sm font-medium transition-colors',
        danger ? 'text-beatz-red hover:bg-beatz-red/10' : 'text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-white/5')}>
      <Icon size={15} /> {label}
    </button>
  )
}
