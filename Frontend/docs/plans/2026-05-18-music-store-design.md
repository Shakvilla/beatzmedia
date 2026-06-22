# Music Store & Product Detail Page (PDP) Design Specification

**Date:** 2026-05-18  
**Author:** Antigravity  
**Status:** Approved / Validated  

---

## 1. Overview & Objectives

BeatzClik is expanding its commercial offerings by introducing a dedicated **Music Store** and standalone **Product Detail Pages (PDPs)**. While existing routes focus on general streaming and basic cart additions, the Music Store serves as a premium digital marketplace. It separates digital asset ownership from streaming, providing an immersive, high-fidelity storefront for purchasing:
- **Hi-Fi Lossless Audio Tracks & Mastered Albums**
- **Beat Licenses & Stems** (Lease, Premium, Exclusive tiers)
- **Physical & Digital Artist Merchandise**
- **Exclusive VIP Fan Experiences & Digital Drops**

---

## 2. Architecture & Routing Hierarchy

To achieve a performant, URL-driven marketplace, the Music Store utilizes a nested routing architecture within TanStack Router. This ensures clean code-splitting, modular tab navigation, and deep-linkable filter states.

### 2.1 Route Tree Hierarchy (`src/routes`)
- `store.tsx`: Parent layout route housing the persistent store header, categorized tab navigation (*Overview*, *Hi-Fi*, *Beats*, *Merch*, *Exclusives*), and master search/filter bar. Renders child routes via `<Outlet />`.
- `store/index.tsx`: Landing view (*Overview* tab) featuring promotional hero carousels, trending beat licenses, and featured merchandise grids.
- `store/hifi.tsx`: Dedicated tab for high-fidelity lossless tracks and albums.
- `store/beats.tsx`: Dedicated tab for beat licenses and stems with license tier filtering.
- `store/merch.tsx`: Dedicated tab for physical and digital artist merchandise.
- `store/exclusives.tsx`: Dedicated tab for VIP fan experiences and limited drops.
- `store/$itemId.tsx`: Standalone Product Detail Page (PDP) route for deep product exploration, licensing configuration, stems preview, and reviews.

### 2.2 URL-Driven State & Search Parameters
A strict Zod validation schema (`storeSearchSchema`) attaches to `store.tsx` to sync filter states with the URL:
```typescript
import { z } from 'zod';

export const storeSearchSchema = z.object({
  q: z.string().optional().catch(''),
  genre: z.string().optional().catch(''),
  price: z.number().optional().catch(undefined),
  tier: z.enum(['LEASE', 'PREMIUM', 'EXCLUSIVE']).optional().catch(undefined),
  sort: z.enum(['popular', 'newest', 'price-asc', 'price-desc']).optional().catch('popular'),
});
```

---

## 3. Components & UI Design System

Dedicated UI components will be built within `src/features/store/components`, strictly adhering to BeatzClik’s rich dark-mode aesthetic, glassmorphism surfaces, vibrant gradients, and micro-animations.

### 3.1 Core Components
- `StoreHeader`: Sticky navigation bar containing categorized tabs, animated active bottom-border indicators, search input, and filter drawer toggles.
- `PremiumProductCard`: Glassmorphism product card featuring hover-zoom image containers, badge overlays (`HI-FI LOSSLESS`, `STEMS INCLUDED`), bold pricing typography, and quick-action buttons.
- `LicenseTierSelector`: Interactive PDP component presenting beat licensing tiers (*Basic Lease*, *Premium Stems*, *Exclusive*) as selectable cards with feature checklists, terms, and dynamic pricing updates.
- `MerchVariantSelector`: Pill-based selector for configuring merchandise attributes (size: S, M, L, XL; color variants) prior to cart addition.
- `StoreHeroBanner`: Dynamic carousel banner for highlighting major drops, artist collaborations, and exclusive discount bundles.

### 3.2 Visual Aesthetics
All components leverage Tailwind utility classes for smooth transitions (`transition-all duration-300 hover:scale-[1.02]`), curated border highlights (`hover:border-beatz-green/50`), and Lucide React icons.

---

## 4. Data Flow, State Management & Cart Integration

### 4.1 Data Fetching & Caching Layer (`src/features/store/services/store.service.ts`)
- **Declarative Queries:** Route components utilize TanStack Query (`useQuery`) with structured query keys: `['store', { tab, q, genre, tier, price }]`. Automatic caching and refetching occur upon search parameter modifications.
- **Hover Prefetching:** To ensure instantaneous navigation to the PDP, `queryClient.prefetchQuery({ queryKey: ['store', 'item', itemId], queryFn: () => getStoreItem(itemId) })` triggers on `PremiumProductCard` hover (`onMouseEnter`).

### 4.2 Cart Integration & State Dispatch
Upon clicking "Add to Cart" on the PDP, the configured item is packaged into a standardized payload:
```typescript
interface CartStoreItem {
  id: string;
  title: string;
  artist: string;
  price: string; // Formatted currency based on selected tier/variant
  image: string;
  type: 'TRACK' | 'ALBUM' | 'BEAT_LICENSE' | 'MERCH';
  metadata?: {
    licenseTier?: 'LEASE' | 'PREMIUM' | 'EXCLUSIVE';
    merchSize?: string;
  };
}
```
This payload is dispatched to the existing Cart state manager (aligning with `src/routes/cart.tsx`). A global toast notification (`toast.success`) provides immediate visual confirmation with a "View Cart" action link.

---

## 5. Error Handling, Fallbacks & Testing Strategy

### 5.1 Error Handling & Fallback UI
- **Query Error Boundaries:** TanStack Query is configured with automatic retries (`retry: 2`). Permanent failures are caught by TanStack Router’s `errorComponent`, rendering `<StoreErrorFallback />` with a refetch prompt.
- **Skeleton Loaders:** `<StoreCatalogSkeleton />` and `<PDPSkeleton />` display shimmering glassmorphism placeholders matching exact card and page dimensions to eliminate layout shifts.
- **Empty States:** `<StoreEmptyState />` renders when filter combinations yield no results, summarizing active filters and providing a 1-click "Reset Filters" action.

### 5.2 Testing Strategy
Validated using Vitest and React Testing Library:
- **Component Unit Tests:** Verify that `<LicenseTierSelector />` and `<MerchVariantSelector />` correctly calculate final pricing and update internal selection states.
- **Integration Tests:** Simulate user workflows to ensure search input changes update URL search parameters via TanStack Router, and cart additions dispatch the correct payload structure.
