/**
 * Global fallback for images that fail to load.
 *
 * <p>Every cover in the app renders through a plain `<img src={...}>` with a URL from the API —
 * 60 of them across 38 files, and not one had an `onError`. When `/images/placeholder.jpg` turned
 * out never to have existed, every track without artwork rendered as the browser's broken-image
 * glyph rather than a neutral tile, and nothing in the UI noticed.
 *
 * Fixing the stored value corrected that instance. This closes the class: a deleted asset, a bad
 * CDN cutover or an expired URL now degrades to the placeholder instead of a broken glyph.
 *
 * WHY ONE LISTENER RATHER THAN 60 PROPS. `error` does not bubble, but it does reach listeners
 * registered in the CAPTURE phase on an ancestor — so a single listener on `document` sees every
 * image failure in the tree, including images added later. The alternative was editing 60 call
 * sites and hoping the 61st remembers.
 *
 * An image that wants its own behaviour opts out with `data-no-fallback`.
 */

export const PLACEHOLDER_IMAGE = '/images/placeholder.svg'

/** Marks an image as already swapped, so a failing placeholder cannot loop forever. */
const HANDLED_ATTR = 'data-fallback-applied'

function onImageError(event: Event): void {
  const target = event.target
  if (!(target instanceof HTMLImageElement)) return
  if (target.hasAttribute('data-no-fallback')) return

  // Already swapped: the placeholder itself failed. Stop here rather than reassigning the same
  // src forever — one broken tile is recoverable, an infinite error loop is not.
  if (target.hasAttribute(HANDLED_ATTR)) return

  // An empty or missing src fires an error in some browsers; swapping is still the right answer,
  // but there is no point comparing it to the placeholder first.
  if (target.getAttribute('src') === PLACEHOLDER_IMAGE) {
    target.setAttribute(HANDLED_ATTR, '')
    return
  }

  target.setAttribute(HANDLED_ATTR, '')
  target.src = PLACEHOLDER_IMAGE
}

/**
 * Starts listening. Returns a cleanup function.
 *
 * Registered with `capture: true` — without it the listener never fires, because image `error`
 * events do not bubble.
 */
export function installImageFallback(doc: Document = document): () => void {
  doc.addEventListener('error', onImageError, true)
  return () => doc.removeEventListener('error', onImageError, true)
}
