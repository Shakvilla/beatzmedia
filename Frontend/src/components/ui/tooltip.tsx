import { useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

interface TooltipProps {
  label: string
  /** Where the tooltip appears relative to the trigger. */
  placement?: 'top' | 'right'
  children: ReactNode
}

/**
 * Lightweight styled tooltip rendered in a portal (never clipped by overflow).
 * Shown on hover/focus of the wrapped element.
 */
export function Tooltip({ label, placement = 'top', children }: TooltipProps) {
  const [coords, setCoords] = useState<{ top: number; left: number } | null>(null)

  const show = (el: HTMLElement) => {
    const r = el.getBoundingClientRect()
    if (placement === 'right') setCoords({ top: r.top + r.height / 2, left: r.right + 10 })
    else setCoords({ top: r.top - 10, left: r.left + r.width / 2 })
  }

  return (
    <span
      className="relative inline-flex"
      onMouseEnter={(e) => show(e.currentTarget)}
      onMouseLeave={() => setCoords(null)}
      onFocus={(e) => show(e.currentTarget)}
      onBlur={() => setCoords(null)}
    >
      {children}
      {coords &&
        createPortal(
          <span
            style={{
              position: 'fixed',
              top: coords.top,
              left: coords.left,
              transform: placement === 'right' ? 'translateY(-50%)' : 'translate(-50%, -100%)',
            }}
            className="pointer-events-none z-[200] whitespace-nowrap rounded-md bg-beatz-dark-bg dark:bg-white px-2 py-1 text-[11px] font-bold text-white dark:text-black shadow-xl animate-in fade-in duration-150"
          >
            {label}
          </span>,
          document.body,
        )}
    </span>
  )
}
