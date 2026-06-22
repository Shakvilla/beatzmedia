import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { Search, CornerDownLeft, User, Disc3, type LucideIcon } from 'lucide-react'
import { cn } from '../../utils/cn'
import { getAdminUsers, getCatalog } from '../../lib/admin-data'

interface NavItem { to: string; icon: LucideIcon; label: string }

interface Result {
  key: string
  label: string
  sub: string
  icon: LucideIcon
  go: () => void
}

/** Global command-palette search across sections, users and catalog. */
export function AdminCommand({ open, onClose, sections }: { open: boolean; onClose: () => void; sections: NavItem[] }) {
  const navigate = useNavigate()
  const [q, setQ] = useState('')
  const [active, setActive] = useState(0)
  const listRef = useRef<HTMLDivElement>(null)

  useEffect(() => { if (!open) setQ('') }, [open])
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    if (open) document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, onClose])

  const results = useMemo<Result[]>(() => {
    const needle = q.trim().toLowerCase()
    const sectionR: Result[] = sections
      .filter((s) => !needle || s.label.toLowerCase().includes(needle))
      .map((s) => ({ key: `s-${s.to}`, label: s.label, sub: 'Section', icon: s.icon, go: () => navigate({ to: s.to }) }))
    if (!needle) return sectionR
    const userR: Result[] = getAdminUsers()
      .filter((u) => `${u.name} ${u.email}`.toLowerCase().includes(needle))
      .slice(0, 5)
      .map((u) => ({ key: `u-${u.id}`, label: u.name, sub: `User · ${u.email}`, icon: User, go: () => navigate({ to: '/admin/users/$userId', params: { userId: u.id } }) }))
    const catalogR: Result[] = getCatalog()
      .filter((c) => `${c.title} ${c.artist}`.toLowerCase().includes(needle))
      .slice(0, 5)
      .map((c) => ({ key: `c-${c.id}`, label: c.title, sub: `Release · ${c.artist}`, icon: Disc3, go: () => navigate({ to: '/admin/catalog/$itemId', params: { itemId: c.id } }) }))
    return [...sectionR, ...userR, ...catalogR]
  }, [q, sections, navigate])

  // Reset / clamp the highlighted row whenever the result set changes.
  useEffect(() => { setActive(0) }, [q])

  // Keep the highlighted row scrolled into view.
  useEffect(() => {
    listRef.current?.querySelector<HTMLElement>(`[data-idx="${active}"]`)?.scrollIntoView({ block: 'nearest' })
  }, [active])

  if (!open) return null
  const pick = (r: Result) => { r.go(); onClose() }

  const onInputKey = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') { e.preventDefault(); setActive((i) => Math.min(i + 1, results.length - 1)) }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setActive((i) => Math.max(i - 1, 0)) }
    else if (e.key === 'Enter' && results[active]) { e.preventDefault(); pick(results[active]) }
  }

  return (
    <div className="fixed inset-0 z-[80] flex items-start justify-center pt-[12vh] px-4">
      <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={onClose} />
      <div className="relative w-full max-w-lg rounded-2xl bg-white dark:bg-beatz-dark-surface-2 border border-gray-200 dark:border-white/10 shadow-2xl overflow-hidden">
        <div className="flex items-center gap-3 px-4 h-14 border-b border-gray-100 dark:border-white/5">
          <Search size={18} className="text-gray-400 shrink-0" />
          <input
            autoFocus
            value={q}
            onChange={(e) => setQ(e.target.value)}
            onKeyDown={onInputKey}
            placeholder="Search users, releases, sections…"
            className="flex-1 bg-transparent text-sm text-beatz-dark-bg dark:text-white placeholder:text-gray-400 focus:outline-none"
          />
          <kbd className="text-[10px] font-mono text-gray-400 border border-gray-200 dark:border-white/10 rounded px-1.5 py-0.5">esc</kbd>
        </div>
        <div ref={listRef} role="listbox" className="max-h-[50vh] overflow-y-auto no-scrollbar py-1">
          {results.length === 0 ? (
            <div className="px-4 py-8 text-center text-sm text-gray-400 dark:text-gray-500">No results for “{q}”.</div>
          ) : results.map((r, i) => (
            <button key={r.key} data-idx={i} role="option" aria-selected={i === active}
              onClick={() => pick(r)} onMouseEnter={() => setActive(i)}
              className={cn('w-full flex items-center gap-3 px-4 py-2.5 text-left transition-colors', i === active ? 'bg-gray-100 dark:bg-white/5' : 'hover:bg-gray-100 dark:hover:bg-white/5')}>
              <span className="w-8 h-8 rounded-lg bg-gray-100 dark:bg-white/10 flex items-center justify-center text-gray-500 dark:text-gray-300 shrink-0"><r.icon size={15} /></span>
              <div className="flex flex-col flex-1 min-w-0">
                <span className="text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{r.label}</span>
                <span className="text-xs text-gray-500 dark:text-gray-400 truncate">{r.sub}</span>
              </div>
              {i === active && <CornerDownLeft size={14} className="text-gray-400 shrink-0" />}
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}
