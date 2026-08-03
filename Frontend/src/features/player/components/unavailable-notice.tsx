import { cn } from '../../../utils/cn'
import { usePlayer } from '../player-context'

/** Single source of truth for the no-playable-stream copy, shared by every transport surface. */
export const UNAVAILABLE_MESSAGE = 'Not available to play right now'

/**
 * What a transport surface shows instead of a progress bar when there is no playable stream.
 *
 * The "Try again" button is not decoration: every play control is disabled while `unavailable`,
 * so without it the state is a dead end — the fan cannot get back to this track without changing
 * track first. It is the only reachable caller of the player's recovery path.
 */
export function UnavailableNotice({
  className,
  tone = 'light',
  compact = false,
}: {
  className?: string
  /** `dark` for surfaces painted over artwork (lyrics view); `light` for normal chrome. */
  tone?: 'light' | 'dark'
  /** Tighter type for the mobile player bar, where vertical space is scarce. */
  compact?: boolean
}) {
  const { retry, retrying } = usePlayer()
  const textClass = tone === 'dark' ? 'text-white/50' : 'text-gray-400 dark:text-gray-500'

  return (
    <span className={cn('flex items-center gap-2', compact ? 'text-[10px]' : 'text-xs', className)}>
      <span className={textClass}>{UNAVAILABLE_MESSAGE}</span>
      <button
        onClick={retry}
        disabled={retrying}
        className={cn(
          'font-bold underline underline-offset-2 disabled:opacity-50 disabled:no-underline transition-colors',
          tone === 'dark' ? 'text-white/80 hover:text-white' : 'text-beatz-green hover:brightness-110',
        )}
      >
        {retrying ? 'Retrying…' : 'Try again'}
      </button>
    </span>
  )
}
