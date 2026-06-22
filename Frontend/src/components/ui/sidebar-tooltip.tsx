import { useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

interface SidebarTooltipProps {
  label: string
  /** Only show the tooltip when the sidebar is collapsed. */
  enabled: boolean
  children: ReactNode
}

/**
 * Styled hover tooltip for collapsed sidebar icons. Rendered in a portal with
 * fixed positioning so it's never clipped by the sidebar's overflow / scroll.
 */
export function SidebarTooltip({ label, enabled, children }: SidebarTooltipProps) {
  const [coords, setCoords] = useState<{ top: number; left: number } | null>(null)

  if (!enabled) return <>{children}</>

  return (
    <div
      className="relative flex"
      onMouseEnter={(e) => {
        const r = e.currentTarget.getBoundingClientRect()
        setCoords({ top: r.top + r.height / 2, left: r.right + 12 })
      }}
      onMouseLeave={() => setCoords(null)}
    >
      {children}
      {coords &&
        createPortal(
          <span
            style={{ position: 'fixed', top: coords.top, left: coords.left, transform: 'translateY(-50%)' }}
            className="pointer-events-none z-[200] whitespace-nowrap rounded-md bg-beatz-dark-bg dark:bg-white px-2.5 py-1.5 text-xs font-bold text-white dark:text-black shadow-xl animate-in fade-in slide-in-from-left-1 duration-150"
          >
            {label}
          </span>,
          document.body,
        )}
    </div>
  )
}
