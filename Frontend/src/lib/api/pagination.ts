export interface PageWire<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export function unwrapPage<T, U>(page: PageWire<T>, map: (t: T) => U): U[] {
  return page.items.map(map)
}
