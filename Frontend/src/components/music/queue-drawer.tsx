import { X, Play, GripVertical } from 'lucide-react'
import { cn } from '../../utils/cn'
import { usePlayer } from '../../features/player/player-context'
import { formatDuration } from '../../lib/format'

export function QueueDrawer() {
  const { queue, currentIndex, isQueueOpen, setQueueOpen, clearQueue, playQueue } = usePlayer()

  const nowPlaying = queue[currentIndex]
  const upNext = queue
    .map((track, index) => ({ track, index }))
    .filter(({ index }) => index !== currentIndex)

  return (
    <>
      {/* Backdrop */}
      <div
        className={cn(
          'fixed inset-0 bg-black/40 backdrop-blur-sm z-[60] transition-opacity duration-500',
          isQueueOpen ? 'opacity-100' : 'opacity-0 pointer-events-none'
        )}
        onClick={() => setQueueOpen(false)}
      />

      {/* Drawer */}
      <div
        className={cn(
          'fixed top-0 right-0 h-full w-full max-w-md bg-[#121212] border-l border-white/5 z-[70] transition-transform duration-500 ease-out flex flex-col shadow-2xl',
          isQueueOpen ? 'translate-x-0' : 'translate-x-full'
        )}
      >
        <div className="p-8 flex items-center justify-between border-b border-white/5">
          <div className="flex flex-col">
            <h2 className="text-2xl font-bold text-white">Up Next</h2>
            <span className="text-xs font-bold text-gray-500 dark:text-gray-300 uppercase tracking-widest mt-1">Playing from your library</span>
          </div>
          <button
            onClick={() => setQueueOpen(false)}
            className="w-10 h-10 rounded-full hover:bg-white/5 flex items-center justify-center text-white transition-colors"
          >
            <X size={24} />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto no-scrollbar p-4 flex flex-col gap-2">
          {queue.length === 0 && (
            <div className="flex flex-col items-center justify-center h-full text-center gap-2 px-8">
              <span className="text-white font-bold">Your queue is empty</span>
              <span className="text-sm text-gray-500 dark:text-gray-300">Play something to build up your queue.</span>
            </div>
          )}

          {nowPlaying && (
            <>
              <div className="px-4 py-2">
                <span className="text-[10px] font-bold text-beatz-green uppercase tracking-[0.2em]">Now Playing</span>
              </div>
              <div className="flex items-center gap-4 p-4 rounded-2xl bg-beatz-green/5 border border-beatz-green/20">
                <div className="w-12 h-12 rounded-lg overflow-hidden shrink-0">
                  <img src={nowPlaying.image} alt={nowPlaying.title} className="w-full h-full object-cover" />
                </div>
                <div className="flex flex-col flex-1 min-w-0">
                  <span className="font-bold text-beatz-green truncate">{nowPlaying.title}</span>
                  <span className="text-xs text-white/60 truncate">{nowPlaying.artistName}</span>
                </div>
                <div className="flex gap-0.5 items-end h-3">
                  <div className="w-1 bg-beatz-green animate-bounce h-2" />
                  <div className="w-1 bg-beatz-green animate-bounce h-3 delay-75" />
                  <div className="w-1 bg-beatz-green animate-bounce h-1.5 delay-150" />
                </div>
              </div>
            </>
          )}

          {upNext.length > 0 && (
            <div className="px-4 py-6">
              <span className="text-[10px] font-bold text-gray-500 dark:text-gray-300 uppercase tracking-[0.2em]">Up Next</span>
            </div>
          )}

          {upNext.map(({ track, index }) => (
            <button
              key={track.id}
              onClick={() => playQueue(queue, index)}
              className="flex items-center gap-4 p-4 rounded-2xl hover:bg-white/5 border border-transparent hover:border-white/5 group transition-all cursor-pointer text-left w-full"
            >
              <div className="flex items-center gap-2 mr-2 opacity-0 group-hover:opacity-100 transition-opacity">
                <GripVertical size={16} className="text-gray-600" />
              </div>
              <div className="w-12 h-12 rounded-lg overflow-hidden shrink-0 bg-white/5 relative">
                <img src={track.image} alt={track.title} className="w-full h-full object-cover group-hover:opacity-40 transition-opacity" />
                <div className="absolute inset-0 flex items-center justify-center opacity-0 group-hover:opacity-100">
                  <Play size={16} className="text-white" fill="currentColor" />
                </div>
              </div>
              <div className="flex flex-col flex-1 min-w-0">
                <span className="font-bold text-white truncate">{track.title}</span>
                <span className="text-xs text-gray-500 dark:text-gray-300 truncate">{track.artistName}</span>
              </div>
              <span className="text-[10px] font-mono text-gray-600 group-hover:text-white transition-colors">{formatDuration(track.duration)}</span>
            </button>
          ))}
        </div>

        <div className="p-8 border-t border-white/5 bg-black/20">
          <button
            onClick={clearQueue}
            className="w-full h-12 rounded-full border border-white/10 text-white text-sm font-bold hover:bg-white/5 transition-all"
          >
            Clear queue
          </button>
        </div>
      </div>
    </>
  )
}
