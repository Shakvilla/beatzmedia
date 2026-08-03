import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'

/**
 * The admin-managed controlled lists.
 *
 * Every one of these used to be hardcoded: genres as a TypeScript union in `types/index.ts`,
 * podcast and event categories as Postgres CHECK constraints, browse tiles in their own table.
 * They now come from `taxonomy_term` (V972) and are editable from the admin console.
 */
export type TaxonomyKind = 'genre' | 'podcast_category' | 'event_category' | 'browse_category'

export interface TaxonomyTermWire {
  id: string
  kind: TaxonomyKind
  slug: string
  label: string
  colorClass: string | null
  sortOrder: number
  active: boolean
}

/** The admin shape adds how many items reference the term. */
export interface AdminTaxonomyTermWire extends TaxonomyTermWire {
  usageCount: number
}

export interface TaxonomyTerm {
  id: string
  kind: TaxonomyKind
  slug: string
  /** What pickers display AND submit — the consuming columns store the label, not the id. */
  label: string
  colorClass: string | null
  sortOrder: number
  active: boolean
}

export interface AdminTaxonomyTerm extends TaxonomyTerm {
  usageCount: number
}

function toTerm(w: TaxonomyTermWire): TaxonomyTerm {
  return {
    id: w.id,
    kind: w.kind,
    slug: w.slug,
    label: w.label,
    colorClass: w.colorClass ?? null,
    sortOrder: w.sortOrder,
    active: w.active,
  }
}

function toAdminTerm(w: AdminTaxonomyTermWire): AdminTaxonomyTerm {
  return { ...toTerm(w), usageCount: w.usageCount ?? 0 }
}

/**
 * `GET /v1/taxonomy?kind=…` — ACTIVE terms only. This is what every picker should use.
 *
 * Taxonomies change rarely and are read on nearly every form, so they are cached for a long time
 * rather than refetched per mount; the admin mutations invalidate these keys explicitly.
 */
export function taxonomyQuery(kind: TaxonomyKind) {
  return queryOptions({
    queryKey: ['taxonomy', kind],
    queryFn: async () => (await apiFetch<TaxonomyTermWire[]>(`/taxonomy?kind=${kind}`)).map(toTerm),
    staleTime: 5 * 60 * 1000,
  })
}

/** `GET /v1/admin/taxonomy?kind=…` — every term including deactivated, each with its usage count. */
export function adminTaxonomyQuery(kind: TaxonomyKind) {
  return queryOptions({
    queryKey: ['admin', 'taxonomy', kind],
    queryFn: async () =>
      (await apiFetch<AdminTaxonomyTermWire[]>(`/admin/taxonomy?kind=${kind}`)).map(toAdminTerm),
  })
}

/** `POST /v1/admin/taxonomy?kind=…` — 201, or 409 when the label already exists. */
export async function apiCreateTerm(
  kind: TaxonomyKind,
  body: { label: string; colorClass?: string | null; sortOrder?: number },
): Promise<AdminTaxonomyTerm> {
  return toAdminTerm(
    await apiFetch<AdminTaxonomyTermWire>(`/admin/taxonomy?kind=${kind}`, {
      method: 'POST',
      body,
    }),
  )
}

/**
 * `PATCH /v1/admin/taxonomy/{id}` — every field optional; omitted means "leave alone".
 * A label change also repoints existing releases/podcasts server-side.
 */
export async function apiUpdateTerm(
  id: string,
  body: { label?: string; colorClass?: string | null; sortOrder?: number; active?: boolean },
): Promise<AdminTaxonomyTerm> {
  return toAdminTerm(
    await apiFetch<AdminTaxonomyTermWire>(`/admin/taxonomy/${id}`, {
      method: 'PATCH',
      body,
    }),
  )
}

/** `DELETE /v1/admin/taxonomy/{id}` — 204, or 409 naming how many items still use the term. */
export async function apiDeleteTerm(id: string): Promise<void> {
  await apiFetch<void>(`/admin/taxonomy/${id}`, { method: 'DELETE' })
}
