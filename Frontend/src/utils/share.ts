/**
 * Copy a link to the clipboard, reporting whether it actually worked.
 *
 * Three call sites previously fired `toast('Link copied to clipboard', 'success')` without
 * touching the clipboard at all — the link was never copied, and the fan was told it was. The
 * point of this helper is that it can FAIL, and returns that fact, so callers cannot claim success
 * they did not earn.
 *
 * `navigator.clipboard` needs a secure context (https or localhost) and can be denied by
 * permissions policy, so the legacy `execCommand` path is a real fallback rather than decoration.
 */
export async function copyLink(url: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(url)
      return true
    }
  } catch {
    // fall through to the legacy path — denied permission, insecure context, etc.
  }

  try {
    const el = document.createElement('textarea')
    el.value = url
    // Keep it out of view and out of the tab order, but still selectable.
    el.setAttribute('readonly', '')
    el.style.position = 'fixed'
    el.style.top = '-9999px'
    document.body.appendChild(el)
    el.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(el)
    return ok
  } catch {
    return false
  }
}

/** Absolute URL for the current page — what a fan actually wants when they hit Share. */
export function currentUrl(): string {
  return window.location.href
}
