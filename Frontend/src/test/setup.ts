/**
 * Node can expose an incomplete experimental `localStorage` when it is started with an invalid
 * `--localstorage-file` value. Vitest workers inherit that object before jsdom starts, leaving
 * browser tests with no `clear()` method. Use a small Storage implementation only in that broken
 * runtime; normal jsdom and browser storage are left untouched.
 */
if (typeof globalThis.localStorage?.clear !== 'function') {
  const values = new Map<string, string>()

  const storage: Storage = {
    get length() {
      return values.size
    },
    clear() {
      values.clear()
    },
    getItem(key) {
      return values.get(String(key)) ?? null
    },
    key(index) {
      return [...values.keys()][index] ?? null
    },
    removeItem(key) {
      values.delete(String(key))
    },
    setItem(key, value) {
      values.set(String(key), String(value))
    },
  }

  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    enumerable: true,
    value: storage,
  })
}
