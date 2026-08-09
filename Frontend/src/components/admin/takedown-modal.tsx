import { useState } from 'react'
import { Modal } from '../ui/modal'
import { cn } from '../../utils/cn'

const REASONS = ['Copyright claim', 'Metadata mismatch', 'Duplicate ISRC', 'Policy violation', 'Other']

/**
 * Confirms a takedown and collects the reason that goes into the permanent audit record.
 *
 * <p>Lifted out of the catalog detail page so the catalog *list* can use it too. The list fired
 * takedown on a single click, with no confirmation and a hardcoded reason —
 * `"Taken down by moderator (quick action from catalog list)"` — which is what got written to
 * `audit_entry.reason` and is therefore all anyone would ever know about why an artist's release
 * was pulled. Verified in QA: one click, no dialog, that exact string in the audit row.
 *
 * Two copies of this dialog would have drifted; the detail page's version was already correct, so
 * this is that version moved rather than a second one written.
 */
export function TakedownModal({ isOpen, title, onClose, onConfirm }: {
  isOpen: boolean
  title: string
  onClose: () => void
  onConfirm: (reason: string) => void
}) {
  const [reason, setReason] = useState('')

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={`Take down “${title}”`}>
      <div className="flex flex-col gap-5">
        <p className="text-sm text-white/70">The release will be removed from BeatzClik and the artist notified. A reason is required and logged.</p>
        <div className="flex flex-wrap gap-2">
          {REASONS.map((r) => (
            <button key={r} onClick={() => setReason(r)}
              className={cn('h-9 px-3.5 rounded-full text-xs font-bold border transition-colors',
                reason === r ? 'border-beatz-red bg-beatz-red/10 text-beatz-red' : 'border-white/10 text-white/70 hover:border-white/20')}>
              {r}
            </button>
          ))}
        </div>
        <input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Add a note…"
          className="w-full h-11 rounded-xl bg-white/5 border border-white/10 px-4 text-white placeholder:text-white/20 focus:outline-none focus:border-beatz-red/60" />
        <div className="flex items-center gap-3">
          <button onClick={onClose} className="flex-1 h-12 rounded-full bg-white/10 text-white font-bold hover:bg-white/15 transition-colors">Cancel</button>
          <button onClick={() => reason.trim() && onConfirm(reason.trim())} disabled={!reason.trim()}
            className="flex-1 h-12 rounded-full bg-beatz-red text-white font-bold hover:bg-beatz-red-light transition-colors disabled:opacity-40">
            Take down
          </button>
        </div>
      </div>
    </Modal>
  )
}

/**
 * Confirms a flag. Separate from takedown because the consequences differ: a flag routes the
 * release into the moderation queue and leaves it live, so the note is optional context for the
 * next moderator rather than the record of why something was pulled.
 *
 * <p>The list previously called `apiFlagCatalog(id)` with no note at all, so the queue entry
 * carried no indication of what the flagger had seen.
 */
export function FlagModal({ isOpen, title, onClose, onConfirm }: {
  isOpen: boolean
  title: string
  onClose: () => void
  onConfirm: (note: string | undefined) => void
}) {
  const [note, setNote] = useState('')

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={`Flag “${title}”`}>
      <div className="flex flex-col gap-5">
        <p className="text-sm text-white/70">The release stays live and is queued for moderator review. A note helps whoever picks it up.</p>
        <input value={note} onChange={(e) => setNote(e.target.value)} placeholder="What did you notice? (optional)"
          className="w-full h-11 rounded-xl bg-white/5 border border-white/10 px-4 text-white placeholder:text-white/20 focus:outline-none focus:border-[#f6c644]/60" />
        <div className="flex items-center gap-3">
          <button onClick={onClose} className="flex-1 h-12 rounded-full bg-white/10 text-white font-bold hover:bg-white/15 transition-colors">Cancel</button>
          <button onClick={() => onConfirm(note.trim() || undefined)}
            className="flex-1 h-12 rounded-full bg-[#f6c644] text-black font-bold hover:brightness-110 transition-all">
            Flag for review
          </button>
        </div>
      </div>
    </Modal>
  )
}
