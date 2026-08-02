import { describe, it, expect } from 'vitest'
import { reducer, initialState, type PlayerState } from './player-context'
import type { Track } from '../../types'

const track = (id: string, duration = 180): Track =>
  ({ id, title: id, artistId: 'a', artistName: 'A', duration, image: '', ownership: 'owned' }) as Track

const playing = (over: Partial<PlayerState> = {}): PlayerState => ({
  ...initialState,
  queue: [track('t1'), track('t2')],
  currentIndex: 0,
  isPlaying: true,
  ...over,
})

describe('SET_PROGRESS', () => {
  it('sets progress to the reported position rather than incrementing', () => {
    const next = reducer(playing({ progress: 4 }), { type: 'SET_PROGRESS', seconds: 41.6 })
    expect(next.progress).toBe(41.6)
  })

  it('does not stop playback near the preview length — the file ending is what stops it', () => {
    const next = reducer(playing({ progress: 29 }), { type: 'SET_PROGRESS', seconds: 30 })
    expect(next.isPlaying).toBe(true)
    expect(next.previewHitLimit).toBe(false)
  })
})

describe('ENDED', () => {
  it('advances to the next track', () => {
    const next = reducer(playing(), { type: 'ENDED', preview: false })
    expect(next.currentIndex).toBe(1)
    expect(next.progress).toBe(0)
  })

  it('replays the same track when repeat is one', () => {
    const next = reducer(playing({ repeat: 'one' }), { type: 'ENDED', preview: false })
    expect(next.currentIndex).toBe(0)
    expect(next.progress).toBe(0)
  })

  it('stops at the end of the queue', () => {
    const next = reducer(playing({ currentIndex: 1 }), { type: 'ENDED', preview: false })
    expect(next.isPlaying).toBe(false)
  })

  it('flags the preview cap and does NOT advance when the ended stream was a preview', () => {
    const next = reducer(playing(), { type: 'ENDED', preview: true })
    expect(next.previewHitLimit).toBe(true)
    expect(next.isPlaying).toBe(false)
    expect(next.currentIndex).toBe(0)
  })

  it('clears unavailable on the track it advances to — a failed track must not poison the next one', () => {
    const next = reducer(playing({ unavailable: true }), { type: 'ENDED', preview: false })
    expect(next.currentIndex).toBe(1)
    expect(next.unavailable).toBe(false)
  })
})

describe('STREAM_ERROR', () => {
  it('marks the track unavailable and stops claiming to play', () => {
    const next = reducer(playing({ progress: 12 }), { type: 'STREAM_ERROR' })
    expect(next.unavailable).toBe(true)
    expect(next.isPlaying).toBe(false)
  })
})

describe('PLAY_TRACK', () => {
  it('clears a previous unavailable state', () => {
    const next = reducer(playing({ unavailable: true }), { type: 'PLAY_TRACK', track: track('t3') })
    expect(next.unavailable).toBe(false)
  })
})
