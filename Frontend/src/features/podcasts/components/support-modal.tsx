import { useState } from 'react'
import { Heart, Smartphone } from 'lucide-react'
import { Modal } from '../../../components/ui/modal'
import { cn } from '../../../utils/cn'

interface SupportModalProps {
  /** Recipient name shown in the body (show or artist). */
  showTitle: string
  /** Dialog heading. Defaults to "Support the show". */
  title?: string
  isOpen: boolean
  onClose: () => void
  onSend: (amount: number) => void
}

const PRESETS = [2, 5, 10, 20, 50]

export function SupportModal({ showTitle, title = 'Support the show', isOpen, onClose, onSend }: SupportModalProps) {
  const [amount, setAmount] = useState(5)
  const [custom, setCustom] = useState('')

  const value = custom ? Number(custom) : amount
  const valid = value > 0

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={title}>
      <div className="flex flex-col gap-6">
        <div className="flex items-center gap-3">
          <div className="w-12 h-12 rounded-full bg-beatz-green/15 flex items-center justify-center shrink-0">
            <Heart className="text-beatz-green" size={22} fill="currentColor" />
          </div>
          <p className="text-sm text-white/70">
            Send a one-off tip to <span className="font-bold text-white">{showTitle}</span>. 100% goes to the creators, paid out via Mobile Money.
          </p>
        </div>

        <div className="grid grid-cols-3 gap-2">
          {PRESETS.map((preset) => (
            <button
              key={preset}
              onClick={() => {
                setAmount(preset)
                setCustom('')
              }}
              className={cn(
                'h-12 rounded-xl font-bold font-mono transition-colors border',
                !custom && amount === preset
                  ? 'bg-beatz-green border-beatz-green text-black'
                  : 'bg-white/5 border-white/10 text-white hover:border-white/30',
              )}
            >
              ₵{preset}
            </button>
          ))}
          <div className="relative">
            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-white/50 font-mono">₵</span>
            <input
              type="number"
              min={1}
              value={custom}
              onChange={(e) => setCustom(e.target.value)}
              placeholder="Other"
              className="w-full h-12 pl-7 pr-2 rounded-xl bg-white/5 border border-white/10 text-white font-mono font-bold placeholder-white/40 focus:outline-none focus:border-beatz-green transition-colors"
            />
          </div>
        </div>

        <button
          disabled={!valid}
          onClick={() => {
            onSend(value)
            onClose()
          }}
          className="h-12 rounded-full bg-beatz-green text-black font-bold flex items-center justify-center gap-2 hover:scale-[1.02] transition-transform disabled:opacity-50 disabled:hover:scale-100"
        >
          <Smartphone size={18} /> Send ₵{valid ? value : 0} with MoMo
        </button>
      </div>
    </Modal>
  )
}
