import { useToast } from '../../components/ui/toast-provider'
import { useCart } from '../cart/cart-context'
import type { Money, StoreItem } from '../../types'

interface AddOptions {
  /** Variant/tier note shown in the cart, e.g. a license tier or merch size. */
  note?: string
  /** Override price (e.g. the selected license tier price). */
  price?: Money
}

/** Adds store items to the real cart and confirms with a toast. */
export function useStoreCart() {
  const { toast } = useToast()
  const { addItem } = useCart()

  function addToCart(item: StoreItem, opts: AddOptions = {}) {
    const note = opts.note
    addItem({
      id: `store:${item.id}${note ? `:${note}` : ''}`,
      kind: 'store',
      title: item.title,
      subtitle: [item.artistName, note].filter(Boolean).join(' • '),
      image: item.image,
      price: opts.price ?? item.price,
      stackable: item.type === 'MERCH',
    })
    toast(`${item.title}${note ? ` (${note})` : ''} added to cart`, 'success')
  }

  return { addToCart }
}
