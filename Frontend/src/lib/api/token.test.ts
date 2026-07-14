import { describe, it, expect, beforeEach } from 'vitest'
import { getToken, setToken, clearToken } from './token'

describe('token storage', () => {
  beforeEach(() => localStorage.clear())

  it('returns null when no token is stored', () => {
    expect(getToken()).toBeNull()
  })

  it('stores and retrieves a token', () => {
    setToken('abc.def.ghi')
    expect(getToken()).toBe('abc.def.ghi')
  })

  it('clears a stored token', () => {
    setToken('abc.def.ghi')
    clearToken()
    expect(getToken()).toBeNull()
  })
})
