import { createFileRoute, Link, getRouteApi } from '@tanstack/react-router'
import { Sparkles, Disc3, AudioLines, Shirt, ArrowRight } from 'lucide-react'
import { PremiumProductCard } from '../features/store/components/premium-product-card'
import { StoreRail, type StoreTabPath } from '../features/store/components/store-rail'
import { useStoreCart } from '../features/store/use-store-cart'
import { beatItems, hifiItems, merchItems, exclusiveItems, filterStoreItems } from '../lib/store-data'
import { formatPrice } from '../lib/format'

export const Route = createFileRoute('/store/')({
  component: StoreOverview,
})

const storeApi = getRouteApi('/store')

const CATEGORIES: { to: StoreTabPath; label: string; caption: string; icon: React.ReactNode; gradient: string }[] = [
  { to: '/store/hifi', label: 'Hi-Fi', caption: 'Lossless audio', icon: <Disc3 size={30} />, gradient: 'from-blue-600 to-indigo-800' },
  { to: '/store/beats', label: 'Beats & Stems', caption: 'License & create', icon: <AudioLines size={30} />, gradient: 'from-emerald-600 to-green-800' },
  { to: '/store/merch', label: 'Merch', caption: 'Official drops', icon: <Shirt size={30} />, gradient: 'from-amber-500 to-orange-700' },
  { to: '/store/exclusives', label: 'Exclusives', caption: 'VIP & limited', icon: <Sparkles size={30} />, gradient: 'from-purple-600 to-fuchsia-800' },
]

function StoreOverview() {
  const { q, sort } = storeApi.useSearch()
  const { addToCart } = useStoreCart()

  // Searching from the header collapses the overview into a single result grid.
  const searchResults = q ? filterStoreItems([...hifiItems, ...beatItems, ...merchItems, ...exclusiveItems], { q, sort }) : null

  if (searchResults) {
    return (
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-x-6 gap-y-10">
        {searchResults.map((item) => (
          <PremiumProductCard key={item.id} item={item} onAdd={addToCart} />
        ))}
        {searchResults.length === 0 && (
          <p className="col-span-full text-gray-500 dark:text-gray-300">No store items match “{q}”.</p>
        )}
      </div>
    )
  }

  const featured = exclusiveItems[0]

  return (
    <div className="flex flex-col gap-16">
      {/* Featured hero */}
      <Link
        to="/store/$itemId"
        params={{ itemId: featured.id }}
        className="relative overflow-hidden rounded-3xl group min-h-[420px] lg:min-h-[460px] flex"
      >
        <img src={featured.image} alt={featured.title} className="absolute inset-0 w-full h-full object-cover transition-transform duration-[800ms] ease-out group-hover:scale-105" />
        <div className="absolute inset-0 bg-gradient-to-r from-black/90 via-black/60 to-transparent" />
        <div className="relative z-10 flex flex-col justify-end gap-5 p-10 lg:p-16 max-w-2xl">
          <span className="flex items-center gap-2 text-xs font-bold tracking-[0.3em] uppercase text-[#f6c644]">
            <Sparkles size={14} /> Exclusive Drop
          </span>
          <h2 className="text-3xl sm:text-4xl lg:text-6xl font-bold text-white tracking-tight leading-[1.05]">{featured.title}</h2>
          <p className="text-lg text-gray-200 max-w-lg leading-relaxed">{featured.description}</p>
          <div className="flex items-center gap-5 mt-2">
            <span className="h-12 px-7 rounded-full bg-beatz-green text-black font-bold flex items-center gap-2 group-hover:scale-105 transition-transform">
              Shop now — from {formatPrice(featured.price)}
              <ArrowRight size={18} />
            </span>
            <span className="text-sm text-white/70 font-bold">{featured.artistName}</span>
          </div>
        </div>
      </Link>

      {/* Browse by category */}
      <section className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {CATEGORIES.map((category) => (
          <Link
            key={category.to}
            to={category.to}
            className={`relative overflow-hidden rounded-2xl p-6 aspect-[16/9] flex flex-col justify-between bg-gradient-to-br ${category.gradient} text-white shadow-md hover:shadow-xl hover:scale-[1.02] transition-all duration-300 group`}
          >
            <div className="relative z-10 flex flex-col">
              <span className="text-title leading-tight">{category.label}</span>
              <span className="text-[11px] font-mono uppercase tracking-widest opacity-80">{category.caption}</span>
            </div>
            <div className="absolute right-4 bottom-4 opacity-25 group-hover:opacity-45 group-hover:scale-110 transition-all duration-300">
              {category.icon}
            </div>
          </Link>
        ))}
      </section>

      {/* Editorial carousels */}
      <StoreRail
        title="Trending beat licenses"
        subtitle="License Ghanaian drill, amapiano & highlife instrumentals"
        viewAllTo="/store/beats"
        items={beatItems}
        onAdd={addToCart}
      />
      <StoreRail
        title="Hi-Fi releases"
        subtitle="Studio-grade lossless masters, yours to keep"
        viewAllTo="/store/hifi"
        items={hifiItems}
        onAdd={addToCart}
      />
      <StoreRail
        title="Official merch"
        subtitle="Tees, hoodies & vinyl straight from the artists"
        viewAllTo="/store/merch"
        items={merchItems}
        onAdd={addToCart}
      />
      <StoreRail
        title="Exclusive drops"
        subtitle="Limited experiences and early access"
        viewAllTo="/store/exclusives"
        items={exclusiveItems}
        onAdd={addToCart}
      />
    </div>
  )
}
