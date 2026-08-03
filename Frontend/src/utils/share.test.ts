/**
 * `copyLink` exists so a Share button cannot claim success it did not earn — three call sites
 * previously toasted "Link copied to clipboard" without touching the clipboard at all. The
 * contract that matters is therefore the FALSE return, not the true one.
 */

import { describe, it, expect, vi, afterEach } from 'vitest'
import { copyLink } from './share'

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('copyLink', () => {
  it('uses the async clipboard API when it is available', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    vi.stubGlobal('navigator', { clipboard: { writeText } })

    await expect(copyLink('https://beatzclik.test/track/abc')).resolves.toBe(true)
    expect(writeText).toHaveBeenCalledWith('https://beatzclik.test/track/abc')
  })

  it('falls back to execCommand when the clipboard API rejects', async () => {
    // Denied permission / insecure context — a real case on non-https origins.
    vi.stubGlobal('navigator', { clipboard: { writeText: vi.fn().mockRejectedValue(new Error('denied')) } })
    const exec = vi.fn().mockReturnValue(true)
    Object.defineProperty(document, 'execCommand', { value: exec, configurable: true })

    await expect(copyLink('https://beatzclik.test/x')).resolves.toBe(true)
    expect(exec).toHaveBeenCalledWith('copy')
  })

  it('reports FALSE when nothing could copy — the case the old code lied about', async () => {
    vi.stubGlobal('navigator', {})
    Object.defineProperty(document, 'execCommand', {
      value: vi.fn().mockReturnValue(false),
      configurable: true,
    })

    await expect(copyLink('https://beatzclik.test/x')).resolves.toBe(false)
  })

  it('reports FALSE rather than throwing when execCommand itself blows up', async () => {
    vi.stubGlobal('navigator', {})
    Object.defineProperty(document, 'execCommand', {
      value: () => {
        throw new Error('boom')
      },
      configurable: true,
    })

    await expect(copyLink('https://beatzclik.test/x')).resolves.toBe(false)
  })
})
