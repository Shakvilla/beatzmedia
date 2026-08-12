import { useCallback, useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { MoreHorizontal, type LucideIcon } from 'lucide-react'
import { cn } from '../../utils/cn'

const MENU_WIDTH = 176 // w-44
const GAP = 6
const EDGE = 8

/**
 * The "…" action menu on an admin table row.
 *
 * <p>Every admin table wraps its rows in `<div className="overflow-x-auto">` so narrow screens can
 * scroll sideways. Per CSS, an `overflow-x` other than `visible` forces the *computed* `overflow-y`
 * from `visible` to `auto` — so that wrapper clips vertically too, silently. The menu used to be an
 * `absolute` child of the row, which meant anything hanging below the last row was cut off, and the
 * `fixed inset-0` click-outside backdrop sat on top of what remained. Clicking a menu item did not
 * fire it; it dismissed the menu.
 *
 * <p>Found while verifying GAP-05: with one release in the catalog the table is only as tall as its
 * single row, so **every** item except the top few pixels was unreachable. In a long table the same
 * thing happens to the last row. Confirmed in the browser — clicking "Take down" closed the menu and
 * issued no request.
 *
 * <p>The panel is therefore portalled to `document.body` and positioned `fixed` against the
 * trigger's viewport rect, which no ancestor can clip. It flips above the trigger when there is not
 * enough room below, and closes on scroll or resize because a fixed panel would otherwise drift away
 * from the row it belongs to.
 */
export function RowMenu({ label = 'Actions', children }: {
  /** Accessible name for the trigger. */
  label?: string
  /** Menu contents. `close` dismisses the menu — call it after invoking the action. */
  children: (close: () => void) => ReactNode
}) {
  const triggerRef = useRef<HTMLButtonElement>(null)
  const panelRef = useRef<HTMLDivElement>(null)
  const [open, setOpen] = useState(false)
  const [pos, setPos] = useState<{ top: number; left: number } | null>(null)

  const close = useCallback(() => setOpen(false), [])

  // Measure after the panel renders: its height depends on how many items the caller supplied.
  useLayoutEffect(() => {
    if (!open) { setPos(null); return }
    const trigger = triggerRef.current
    const panel = panelRef.current
    if (!trigger || !panel) return
    const rect = trigger.getBoundingClientRect()
    const height = panel.offsetHeight
    const below = rect.bottom + GAP
    const top = below + height > window.innerHeight - EDGE
      ? Math.max(EDGE, rect.top - GAP - height) // no room below — flip above
      : below
    const left = Math.max(EDGE, Math.min(rect.right - MENU_WIDTH, window.innerWidth - MENU_WIDTH - EDGE))
    setPos({ top, left })
  }, [open, children])

  useEffect(() => {
    if (!open) return
    // `capture` so a scroll inside the table wrapper closes it too, not just a page scroll.
    window.addEventListener('scroll', close, true)
    window.addEventListener('resize', close)
    return () => {
      window.removeEventListener('scroll', close, true)
      window.removeEventListener('resize', close)
    }
  }, [open, close])

  return (
    <>
      <button ref={triggerRef} onClick={() => setOpen((o) => !o)} aria-label={label} aria-expanded={open}
        className="w-8 h-8 flex items-center justify-center rounded-full text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white hover:bg-gray-100 dark:hover:bg-white/10 transition-colors">
        <MoreHorizontal size={18} />
      </button>
      {open && createPortal(
        <>
          <div className="fixed inset-0 z-40" onClick={close} />
          <div ref={panelRef} role="menu"
            // Rendered off-screen for the first paint so the measuring pass never shows a flash at
            // the wrong position.
            style={pos ?? { top: -9999, left: -9999 }}
            className="fixed z-50 w-44 py-1 rounded-xl bg-white dark:bg-beatz-dark-surface-2 border border-gray-200 dark:border-white/10 shadow-xl">
            {children(close)}
          </div>
        </>,
        document.body,
      )}
    </>
  )
}

/** One row in a {@link RowMenu}. */
export function MenuItem({ icon: Icon, label, onClick, disabled, danger }: {
  icon: LucideIcon
  label: string
  onClick: () => void
  disabled?: boolean
  danger?: boolean
}) {
  return (
    <button role="menuitem" onClick={onClick} disabled={disabled}
      className={cn('w-full flex items-center gap-2.5 px-3 py-2 text-sm font-medium transition-colors',
        disabled ? 'text-gray-300 dark:text-gray-600 cursor-not-allowed'
          : danger ? 'text-beatz-red hover:bg-beatz-red/10'
            : 'text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-white/5')}>
      <Icon size={15} /> {label}
    </button>
  )
}
