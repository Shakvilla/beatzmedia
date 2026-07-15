import { useState } from 'react'
import { Plus, Check, ListMusic, Lock, ShoppingCart } from 'lucide-react'
import { Modal } from '../../../components/ui/modal'
import { useToast } from '../../../components/ui/toast-provider'
import { useCart } from '../../cart/cart-context'
import { useCollection } from '../collection-context'
import { getTrack, trackAccessible } from '../../../lib/mock-data'
import { formatPrice } from '../../../lib/format'

interface AddToPlaylistModalProps {
  /** Track being added; modal is only meaningful when set. */
  trackId: string | null
  isOpen: boolean
  onClose: () => void
}

export function AddToPlaylistModal({ trackId, isOpen, onClose }: AddToPlaylistModalProps) {
  const { userPlaylists, isTrackInPlaylist, addTrackToPlaylist, removeTrackFromPlaylist, createPlaylist } = useCollection()
  const { addItem } = useCart()
  const { toast } = useToast()
  const [creating, setCreating] = useState(false)
  const [name, setName] = useState('')

  const track = trackId ? getTrack(trackId) : undefined
  const locked = track ? !trackAccessible(track) : false

  const toggle = (playlistId: string, title: string) => {
    if (!trackId) return
    if (isTrackInPlaylist(playlistId, trackId)) {
      removeTrackFromPlaylist(playlistId, trackId)
    } else {
      addTrackToPlaylist(playlistId, trackId)
      toast(`Added to ${title}`, 'success')
    }
  }

  const createAndAdd = async () => {
    const title = name.trim()
    if (!title || !trackId) return
    await createPlaylist(title, trackId)
    toast(`Added to ${title}`, 'success')
    setName('')
    setCreating(false)
  }

  if (locked && track) {
    return (
      <Modal isOpen={isOpen} onClose={onClose} title="Add to playlist">
        <div className="flex flex-col items-center text-center gap-4 py-2">
          <div className="w-12 h-12 rounded-full bg-[#f6c644]/15 flex items-center justify-center">
            <Lock className="text-[#f6c644]" size={22} />
          </div>
          <p className="text-sm text-white/70">
            <span className="font-bold text-white">{track.title}</span> is a premium track. Buy it once and it’ll be addable to any playlist.
          </p>
          <button
            onClick={() => {
              addItem({ id: `track:${track.id}`, kind: 'track', title: track.title, subtitle: track.artistName, image: track.image, price: track.price ?? { amount: 0, currency: 'GHS' } })
              toast(`“${track.title}” added to cart`, 'success')
              onClose()
            }}
            className="h-11 px-6 rounded-full bg-beatz-green text-black font-bold flex items-center gap-2"
          >
            <ShoppingCart size={16} /> Add to cart • {formatPrice(track.price)}
          </button>
        </div>
      </Modal>
    )
  }

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Add to playlist">
      <div className="flex flex-col gap-3">
        {creating ? (
          <div className="flex items-center gap-2">
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && createAndAdd()}
              autoFocus
              placeholder="New playlist name"
              className="flex-1 h-11 px-4 rounded-xl bg-white/5 border border-white/10 text-white placeholder-white/40 focus:outline-none focus:border-beatz-green transition-colors"
            />
            <button onClick={createAndAdd} disabled={!name.trim()} className="h-11 px-5 rounded-full bg-beatz-green text-black font-bold disabled:opacity-50">
              Add
            </button>
          </div>
        ) : (
          <button
            onClick={() => setCreating(true)}
            className="flex items-center gap-3 p-3 rounded-xl hover:bg-white/5 text-left transition-colors"
          >
            <div className="w-11 h-11 rounded-lg bg-white/10 flex items-center justify-center shrink-0">
              <Plus className="text-beatz-green" size={20} />
            </div>
            <span className="font-bold text-white">New playlist</span>
          </button>
        )}

        <div className="flex flex-col gap-1 max-h-72 overflow-y-auto no-scrollbar">
          {userPlaylists.map((p) => {
            const inIt = trackId ? isTrackInPlaylist(p.id, trackId) : false
            return (
              <button
                key={p.id}
                onClick={() => toggle(p.id, p.title)}
                className="flex items-center justify-between gap-3 p-3 rounded-xl hover:bg-white/5 transition-colors text-left"
              >
                <div className="flex items-center gap-3 min-w-0">
                  <div className="w-11 h-11 rounded-lg bg-white/5 flex items-center justify-center shrink-0">
                    <ListMusic size={18} className="text-white/60" />
                  </div>
                  <div className="flex flex-col min-w-0">
                    <span className="font-bold text-white truncate">{p.title}</span>
                    <span className="text-xs text-white/50">{p.trackIds.length} songs</span>
                  </div>
                </div>
                <span className={`w-7 h-7 rounded-full flex items-center justify-center shrink-0 ${inIt ? 'bg-beatz-green text-black' : 'border border-white/20 text-white/60'}`}>
                  {inIt ? <Check size={15} strokeWidth={3} /> : <Plus size={15} />}
                </span>
              </button>
            )
          })}
          {userPlaylists.length === 0 && (
            <p className="text-white/50 text-sm py-6 text-center">No playlists yet — create one above.</p>
          )}
        </div>
      </div>
    </Modal>
  )
}
