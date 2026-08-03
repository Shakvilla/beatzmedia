/**
 * "Add this track to the cart and say so" — the one action three surfaces need (the player bar,
 * the lyrics view and the track page) and were each about to spell out for themselves.
 */

import { useCart } from './cart-context'
import { useToast } from '../../components/ui/toast-provider'
import type { Track } from '../../types'

export function useBuyTrack(): (track: Track) => void {
  const { addItem } = useCart()
  const { toast } = useToast()

  return (track: Track) => {
    addItem({
      id: `track:${track.id}`,
      kind: 'track',
      title: track.title,
      subtitle: track.artistName,
      image: track.image,
      price: track.price ?? { amount: 0, currency: 'GHS' },
    })
    toast(`“${track.title}” added to cart`, 'success')
  }
}
