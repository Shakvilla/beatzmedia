import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Toggle } from '../../components/ui/toggle'
import { useToast } from '../../components/ui/toast-provider'
import { useAuth } from '../auth/auth-context'
import { ApiError } from '../../lib/api/errors'
import {
  apiClearListeningHistory,
  apiUpdateListeningSettings,
  listeningSettingsQuery,
} from '../../lib/api/queries/engagement'

export function ListeningHistorySettings() {
  const { isAuthenticated } = useAuth()
  const { toast } = useToast()
  const queryClient = useQueryClient()
  const { data: settings } = useQuery(listeningSettingsQuery(isAuthenticated))
  const [busy, setBusy] = useState(false)

  const setEnabled = async (enabled: boolean) => {
    if (busy) return
    setBusy(true)
    try {
      await apiUpdateListeningSettings(enabled)
      await queryClient.invalidateQueries({ queryKey: ['me', 'listening'] })
      toast(enabled ? 'Listening history enabled' : 'Listening history paused', 'success')
    } catch (error) {
      toast(error instanceof ApiError ? error.message : 'Could not update listening history', 'error')
    } finally {
      setBusy(false)
    }
  }

  const clearHistory = async () => {
    if (busy || !window.confirm('Clear your complete listening history? This cannot be undone.')) return
    setBusy(true)
    try {
      await apiClearListeningHistory()
      await queryClient.invalidateQueries({ queryKey: ['me', 'listening'] })
      toast('Listening history cleared', 'success')
    } catch (error) {
      toast(error instanceof ApiError ? error.message : 'Could not clear listening history', 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <div className="flex items-center justify-between gap-4 border-b border-dashed border-gray-200 py-3 dark:border-white/5">
        <div className="flex min-w-0 flex-col">
          <span className="text-sm font-bold text-beatz-dark-bg dark:text-white">Listening history</span>
          <span className="text-xs text-gray-500 dark:text-gray-400">Save recently played items and podcast progress across devices</span>
        </div>
        <Toggle
          checked={settings?.historyEnabled ?? true}
          onChange={setEnabled}
          label="Listening history"
        />
      </div>
      <div className="flex items-center justify-between gap-4 border-b border-dashed border-gray-200 py-3 dark:border-white/5">
        <div className="flex min-w-0 flex-col">
          <span className="text-sm font-bold text-beatz-dark-bg dark:text-white">Listening history data</span>
          <span className="text-xs text-gray-500 dark:text-gray-400">Recently played items and saved progress</span>
        </div>
        <button
          type="button"
          onClick={clearHistory}
          disabled={busy}
          className="shrink-0 text-sm font-bold text-beatz-red hover:underline disabled:opacity-40"
        >
          Clear history
        </button>
      </div>
    </>
  )
}
