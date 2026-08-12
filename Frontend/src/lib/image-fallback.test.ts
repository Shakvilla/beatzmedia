import { describe, it, expect, afterEach } from 'vitest'
import { installImageFallback, PLACEHOLDER_IMAGE } from './image-fallback'

/**
 * These drive the real `error` event rather than calling the handler directly — the whole point of
 * the module is that a capture-phase listener SEES image errors at all, and calling the handler by
 * hand would pass even if `capture: true` were dropped and nothing ever fired.
 */
describe('installImageFallback', () => {
  let uninstall: (() => void) | null = null

  afterEach(() => {
    uninstall?.()
    uninstall = null
    document.body.innerHTML = ''
  })

  const addImage = (src: string, attrs: Record<string, string> = {}) => {
    const img = document.createElement('img')
    img.setAttribute('src', src)
    for (const [k, v] of Object.entries(attrs)) img.setAttribute(k, v)
    document.body.appendChild(img)
    return img
  }

  /** jsdom never loads images, so the failure is simulated by dispatching the event it would fire. */
  const fail = (img: HTMLImageElement) =>
    img.dispatchEvent(new Event('error', { bubbles: false, cancelable: false }))

  it('swaps a broken image for the placeholder', () => {
    uninstall = installImageFallback()
    const img = addImage('/v1/media/images/gone')

    fail(img)

    expect(img.getAttribute('src')).toBe(PLACEHOLDER_IMAGE)
  })

  it('does not loop when the placeholder itself fails', () => {
    // The failure mode that matters: reassigning src on error re-triggers error, and without a
    // guard the browser spins on it forever.
    uninstall = installImageFallback()
    const img = addImage('/v1/media/images/gone')

    fail(img)
    expect(img.getAttribute('src')).toBe(PLACEHOLDER_IMAGE)

    fail(img) // the placeholder is missing too
    fail(img)

    expect(img.getAttribute('src')).toBe(PLACEHOLDER_IMAGE)
  })

  it('leaves an image alone when it opts out', () => {
    uninstall = installImageFallback()
    const img = addImage('/v1/media/images/gone', { 'data-no-fallback': '' })

    fail(img)

    expect(img.getAttribute('src')).toBe('/v1/media/images/gone')
  })

  it('covers images added after install', () => {
    // The reason this is one document listener instead of 60 onError props.
    uninstall = installImageFallback()
    const img = addImage('/late/arrival.png')

    fail(img)

    expect(img.getAttribute('src')).toBe(PLACEHOLDER_IMAGE)
  })

  it('ignores errors from non-image elements', () => {
    // <audio> already has its own error handling in the player; this listener must not touch it.
    uninstall = installImageFallback()
    const audio = document.createElement('audio')
    audio.setAttribute('src', '/audio/track.m4a')
    document.body.appendChild(audio)

    audio.dispatchEvent(new Event('error'))

    expect(audio.getAttribute('src')).toBe('/audio/track.m4a')
  })

  it('stops swapping once uninstalled', () => {
    const stop = installImageFallback()
    stop()
    const img = addImage('/v1/media/images/gone')

    fail(img)

    expect(img.getAttribute('src')).toBe('/v1/media/images/gone')
  })
})
