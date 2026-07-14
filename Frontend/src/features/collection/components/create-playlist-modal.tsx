import { useState } from 'react'
import { ListPlus } from 'lucide-react'
import { Modal } from '../../../components/ui/modal'
import { useCollection } from '../collection-context'

interface CreatePlaylistModalProps {
  isOpen: boolean
  onClose: () => void
  /** Called with the new playlist id after creation. */
  onCreated?: (id: string) => void
}

export function CreatePlaylistModal({ isOpen, onClose, onCreated }: CreatePlaylistModalProps) {
  const { createPlaylist } = useCollection()
  const [name, setName] = useState('')

  const submit = async () => {
    const title = name.trim()
    if (!title) return
    const id = await createPlaylist(title)
    setName('')
    onClose()
    onCreated?.(id)
  }

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Create playlist">
      <div className="flex flex-col gap-5">
        <div className="flex items-center gap-3">
          <div className="w-12 h-12 rounded-lg bg-beatz-green/15 flex items-center justify-center shrink-0">
            <ListPlus className="text-beatz-green" size={22} />
          </div>
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && submit()}
            autoFocus
            placeholder="Playlist name"
            className="flex-1 h-12 px-4 rounded-xl bg-white/5 border border-white/10 text-white placeholder-white/40 focus:outline-none focus:border-beatz-green transition-colors"
          />
        </div>
        <button
          onClick={submit}
          disabled={!name.trim()}
          className="h-12 rounded-full bg-beatz-green text-black font-bold hover:scale-[1.02] transition-transform disabled:opacity-50 disabled:hover:scale-100"
        >
          Create playlist
        </button>
      </div>
    </Modal>
  )
}
