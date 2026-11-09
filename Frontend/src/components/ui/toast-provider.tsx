import React, { createContext, useContext, useState, useCallback } from 'react'
import { X, CheckCircle, AlertCircle, Info } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { cn } from '../../utils/cn'

type ToastType = 'success' | 'error' | 'info'

interface Toast {
  id: string
  message: string
  type: ToastType
  action?: ToastAction
}

interface ToastAction {
  label: string
  onClick: () => void | Promise<void>
}

interface ToastContextType {
  toast: (message: string, type?: ToastType, action?: ToastAction) => void
}

const ToastContext = createContext<ToastContextType | undefined>(undefined)

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])

  const toast = useCallback((
    message: string,
    type: ToastType = 'info',
    action?: ToastAction,
  ) => {
    const id = Math.random().toString(36).substring(2, 9)
    setToasts((prev) => [...prev, { id, message, type, action }])
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id))
    }, 3000)
  }, [])

  return (
    <ToastContext.Provider value={{ toast }}>
      {children}
      <div className="fixed bottom-28 right-8 z-[200] flex flex-col gap-3 pointer-events-none">
        {toasts.map((t) => (
          <ToastItem key={t.id} toast={t} onClose={() => setToasts((prev) => prev.filter((item) => item.id !== t.id))} />
        ))}
      </div>
    </ToastContext.Provider>
  )
}

function ToastItem({ toast, onClose }: { toast: Toast, onClose: () => void }) {
  const icons: Record<ToastType, LucideIcon> = {
    success: CheckCircle,
    error: AlertCircle,
    info: Info,
  }
  const Icon = icons[toast.type]

  return (
    <div className={cn(
      "min-w-[280px] p-4 rounded-2xl border flex items-center gap-4 shadow-2xl animate-in slide-in-from-right-10 fade-in duration-300 pointer-events-auto",
      toast.type === 'success' && "bg-[#1db954] border-[#1db954]/20 text-black",
      toast.type === 'error' && "bg-red-500 border-red-500/20 text-white",
      toast.type === 'info' && "bg-beatz-dark-surface-2 border-white/10 text-white"
    )}>
      <Icon size={20} className={cn(toast.type === 'success' ? "text-black" : "text-white")} />
      <span className="flex-1 font-bold text-sm">{toast.message}</span>
      {toast.action && (
        <button
          type="button"
          onClick={() => {
            onClose()
            void toast.action?.onClick()
          }}
          className="rounded-full border border-current/30 px-3 py-1 text-xs font-black uppercase tracking-wider hover:bg-black/10 dark:hover:bg-white/10"
        >
          {toast.action.label}
        </button>
      )}
      <button onClick={onClose} className="opacity-60 hover:opacity-100 transition-opacity">
        <X size={16} />
      </button>
    </div>
  )
}

// The provider and its hook intentionally share this module; all existing consumers import this
// stable public surface, so splitting it solely for Fast Refresh would create a repository-wide edit.
// eslint-disable-next-line react-refresh/only-export-components
export function useToast() {
  const context = useContext(ToastContext)
  if (!context) throw new Error('useToast must be used within a ToastProvider')
  return context
}
