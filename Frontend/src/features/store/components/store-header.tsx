import { getRouteApi, Link } from '@tanstack/react-router'
import { Search, X } from 'lucide-react'
import type { StoreSort } from '../../../types'

const storeApi = getRouteApi('/store')

const TABS = [
  { to: '/store', label: 'Overview', exact: true },
  { to: '/store/hifi', label: 'Hi-Fi', exact: false },
  { to: '/store/beats', label: 'Beats', exact: false },
  { to: '/store/merch', label: 'Merch', exact: false },
  { to: '/store/exclusives', label: 'Exclusives', exact: false },
] as const

const SORT_OPTIONS: { value: StoreSort; label: string }[] = [
  { value: 'popular', label: 'Most popular' },
  { value: 'newest', label: 'Newest' },
  { value: 'price-asc', label: 'Price: low to high' },
  { value: 'price-desc', label: 'Price: high to low' },
]

export function StoreHeader() {
  const search = storeApi.useSearch()
  const navigate = storeApi.useNavigate()

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-2">
        <span className="text-xs font-bold tracking-[0.3em] uppercase text-beatz-green">Music Store</span>
        <h1 className="text-display tracking-tight text-beatz-dark-bg dark:text-white">Buy. Own. Support.</h1>
        <p className="text-gray-500 dark:text-gray-300 max-w-2xl">
          Lossless tracks, beat licenses, official merch and exclusive drops — pay with MoMo, own forever.
        </p>
      </div>

      {/* Tabs */}
      <nav className="flex items-center gap-6 overflow-x-auto no-scrollbar border-b border-gray-200 dark:border-white/5">
        {TABS.map((tab) => (
          <Link
            key={tab.to}
            to={tab.to}
            activeOptions={{ exact: tab.exact }}
            className="relative px-1 py-3 text-sm font-bold whitespace-nowrap text-gray-500 dark:text-gray-300 hover:text-beatz-dark-bg dark:hover:text-white transition-colors [&.active]:text-beatz-dark-bg dark:[&.active]:text-white [&.active>span]:scale-x-100"
          >
            {tab.label}
            <span className="absolute left-0 right-0 -bottom-px h-0.5 bg-beatz-green rounded-full scale-x-0 transition-transform origin-left" />
          </Link>
        ))}
      </nav>

      {/* Search + sort */}
      <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-400" size={20} />
          <input
            type="text"
            value={search.q ?? ''}
            onChange={(e) => navigate({ search: (prev) => ({ ...prev, q: e.target.value || undefined }) })}
            placeholder="Search the store…"
            className="w-full h-12 pl-12 pr-10 rounded-full bg-white dark:bg-beatz-dark-surface-2 text-beatz-dark-bg dark:text-white placeholder-gray-400 dark:placeholder-beatz-light-surface-3 border-2 border-transparent focus:border-beatz-green outline-none transition-colors shadow-sm"
          />
          {search.q && (
            <button
              onClick={() => navigate({ search: (prev) => ({ ...prev, q: undefined }) })}
              className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white transition-colors"
              aria-label="Clear search"
            >
              <X size={18} />
            </button>
          )}
        </div>

        <div className="flex items-center gap-2 shrink-0">
          <label htmlFor="store-sort" className="text-[10px] font-bold uppercase tracking-widest text-gray-500 dark:text-gray-300">
            Sort
          </label>
          <select
            id="store-sort"
            value={search.sort ?? 'popular'}
            onChange={(e) => navigate({ search: (prev) => ({ ...prev, sort: e.target.value as StoreSort }) })}
            className="h-12 px-4 rounded-full bg-white dark:bg-beatz-dark-surface-2 border border-gray-200 dark:border-transparent text-beatz-dark-bg dark:text-white text-sm font-bold focus:outline-none focus:border-beatz-green transition-colors cursor-pointer shadow-sm"
          >
            {SORT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value} className="bg-white dark:bg-[#181818]">
                {option.label}
              </option>
            ))}
          </select>
        </div>
      </div>
    </div>
  )
}
