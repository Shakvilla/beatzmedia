/**
 * ADR-35 — MP3 is accepted, but the wizard must say so honestly.
 *
 * Two things are worth guarding. `isLossyAudio` decides whether the warning appears at all, and
 * browsers are inconsistent about `File.type` (some report `audio/mpeg`, some `audio/mp3`, some
 * an empty string), so the extension fallback is load-bearing rather than belt-and-braces.
 * `LossySourceNotice` must then actually render — the backend now accepts MP3 silently, so if the
 * notice regresses the artist gets no signal at all that quality was left on the table.
 */

import { render, screen, cleanup } from '@testing-library/react'
import { describe, it, expect, afterEach } from 'vitest'
import { isLossyAudio, type UploadedTrack } from './release-draft-context'
import { LossySourceNotice } from '../../routes/studio.release.new.tracks'

afterEach(cleanup)

const file = (name: string, type: string) => new File([new Uint8Array([1, 2, 3])], name, { type })

const track = (over: Partial<UploadedTrack> = {}): UploadedTrack => ({
  id: 't1',
  title: 'Sunset Groove',
  duration: 180,
  status: 'ready',
  progress: 100,
  src: '',
  price: 5,
  explicit: false,
  ...over,
})

describe('isLossyAudio', () => {
  it('flags mp3 by either MIME spelling', () => {
    expect(isLossyAudio(file('a.mp3', 'audio/mpeg'))).toBe(true)
    expect(isLossyAudio(file('a.mp3', 'audio/mp3'))).toBe(true)
  })

  it('flags mp3 by extension when the browser reports no type', () => {
    expect(isLossyAudio(file('Sunset Groove.MP3', ''))).toBe(true)
  })

  it('does not flag the lossless masters', () => {
    expect(isLossyAudio(file('a.wav', 'audio/wav'))).toBe(false)
    expect(isLossyAudio(file('a.flac', 'audio/flac'))).toBe(false)
  })
})

describe('LossySourceNotice', () => {
  it('renders nothing when every source is lossless', () => {
    const { container } = render(<LossySourceNotice tracks={[track(), track({ id: 't2' })]} />)
    expect(container.textContent).toBe('')
  })

  it('names the track when exactly one is lossy', () => {
    render(<LossySourceNotice tracks={[track({ lossy: true })]} />)
    expect(screen.getByText(/“Sunset Groove” is an MP3\./)).toBeTruthy()
  })

  it('counts them when several are lossy', () => {
    render(
      <LossySourceNotice
        tracks={[track({ lossy: true }), track({ id: 't2', lossy: true }), track({ id: 't3' })]}
      />,
    )
    expect(screen.getByText(/2 tracks are MP3s\./)).toBeTruthy()
  })

  it('is advisory, not a blocker — it offers the better path without refusing the upload', () => {
    render(<LossySourceNotice tracks={[track({ lossy: true })]} />)
    // No control here may prevent proceeding; the notice is a <div role="status">, and an
    // artist who only has an MP3 must still be able to release.
    expect(screen.queryByRole('button')).toBeNull()
    expect(screen.getByRole('status')).toBeTruthy()
    expect(screen.getByText(/upload that instead/)).toBeTruthy()
  })
})
