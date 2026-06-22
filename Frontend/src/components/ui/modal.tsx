import { X } from 'lucide-react'
import { cn } from '../../utils/cn'
import { useEffect, useRef } from 'react'

interface ModalProps {
  isOpen: boolean
  onClose: () => void
  title?: string
  children: React.ReactNode
  className?: string
}

export function Modal({ isOpen, onClose, title, children, className }: ModalProps) {
  const modalRef = useRef<HTMLDivElement>(null)
  const restoreRef = useRef<HTMLElement | null>(null)

  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    if (isOpen) {
      restoreRef.current = document.activeElement as HTMLElement | null
      document.addEventListener('keydown', handleEscape)
      document.body.style.overflow = 'hidden'
      // Move focus into the dialog for keyboard + screen-reader users.
      requestAnimationFrame(() => {
        const el = modalRef.current
        el?.querySelector<HTMLElement>('input, textarea, select, button')?.focus()
      })
    }
    return () => {
      document.removeEventListener('keydown', handleEscape)
      document.body.style.overflow = 'unset'
      if (isOpen) restoreRef.current?.focus?.()
    }
  }, [isOpen, onClose])

  if (!isOpen) return null

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 lg:p-8">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-black/60 backdrop-blur-sm animate-in fade-in duration-300"
        onClick={onClose}
      />

      {/* Modal Content */}
      <div
        ref={modalRef}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className={cn(
          "relative w-full max-w-lg bg-[#181818] border border-white/5 rounded-3xl shadow-2xl overflow-hidden animate-in zoom-in-95 fade-in duration-300",
          className
        )}
      >
        {/* Header */}
        <div className="p-6 flex items-center justify-between border-b border-white/5">
          {title && <h3 className="text-xl font-bold text-white tracking-tight">{title}</h3>}
          <button 
            onClick={onClose}
            className="w-10 h-10 rounded-full hover:bg-white/5 flex items-center justify-center text-white/60 hover:text-white transition-colors ml-auto"
          >
            <X size={20} />
          </button>
        </div>

        {/* Body */}
        <div className="p-6">
          {children}
        </div>
      </div>
    </div>
  )
}
