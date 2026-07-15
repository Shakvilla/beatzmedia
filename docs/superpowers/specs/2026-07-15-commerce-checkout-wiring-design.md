# Slice 3 — Commerce (cart → checkout → orders → ownership) wiring design

## Context

BeatzClik's frontend SPA (`Frontend/`) is a finished, mock-backed functional spec. Prior slices wired
it to the real Quarkus backend (`beatzmedia`) incrementally, with no visual change:

- **Slice 1** (PR #121) — foundation + auth + catalog, established the `loader` +
  `queryClient.ensureQueryData` + `useSuspenseQuery` pattern for read paths.
- **Slice 2a** (PR #123) — backend `POST /v1/catalog/resolve` batch endpoint.
- **Slice 2b** (PR #124) — collection + library, established the TanStack Query optimistic-mutation
  pattern (`setQueryData` + rollback + toast + invalidate-on-settle) for write paths.

This slice wires **commerce**: `Frontend/src/features/cart/cart-context.tsx`, `routes/cart.tsx`,
`routes/checkout.index.tsx`, `routes/checkout.complete.tsx`. Today the entire flow is a client-only
simulation — `CartProvider` persists to `localStorage`, `checkout()` fabricates a `BZ-YYYY-#####`
reference synchronously, and `checkout.complete.tsx` calls `markTracksOwned` locally. No money moves,
no server is involved, and ownership is asserted, not granted.

The backend commerce module (`commerce/`) is feature-complete: `GET/POST/PATCH/DELETE /v1/me/cart`,
`POST /v1/checkout`, `GET /v1/me/orders`. `CartItemView` maps almost 1:1 onto the frontend's `CartItem`.
The catch is that real checkout is **asynchronous** — MoMo settlement happens on the user's phone,
`POST /v1/checkout` returns `202 Accepted` with status `pending`, and a webhook (or the recon poll)
settles the order to `paid`/`fulfilled` (or `failed`) seconds later. Ownership is granted only on
confirmed settlement (INV-1) — never optimistically. The mock's synchronous "pay → instant receipt →
instant unlock" UX cannot be preserved as-is; this design makes that gap explicit and honest.

## Decisions (confirmed with the user during brainstorming)

1. **Settlement UX — poll a new single-order endpoint.** Add `GET /v1/me/orders/{orderId}` (doesn't
   exist today; `OrderResource` only lists) and poll it after the `202`. Show an "Authorizing on your
   phone…" pending state, render the existing "Payment confirmed" receipt only once status is
   `paid`/`fulfilled`. This is a genuinely new screen state — unavoidable, since MoMo settlement is
   asynchronous and the alternative is telling the user their payment succeeded before it has.
2. **Guest cart — local cart, merge on login.** Anonymous users keep today's exact `localStorage`
   cart (no route guard changes, all 11 existing `addItem` call sites keep working). On the
   false→true auth transition, each local line is POSTed into the server cart and the local cart is
   cleared. Costs one merge-on-login path with its edge cases (duplicate lines, buy-once items already
   owned) but preserves the current logged-out UX exactly.
3. **Card redirect — deferred.** `PSP_REDDE` is off in every environment (a documented human gate
   pending real Redde credentials); the sandbox gateway settles card the same as MoMo today.
   `CheckoutResult` has no `checkoutUrl` field, so a hosted-checkout redirect is unreachable through
   `/v1/checkout` as it stands. Building a redirect now would be unverifiable against a real gateway.
   Card keeps its existing picker button and rides the same `202` + poll path; the redirect is logged
   as a follow-up (`WU-COM-4`, alongside removing the checkout-kind gate — see Decision 4) tied to the
   Redde credential gate.
4. **G3 pricing gate — already closed at checkout; wire cart-add for all kinds, handle the checkout
   rejection honestly.** Correcting an assumption from initial scoping: `CheckoutService.gateKind()`
   already hard-rejects `episode`/`season-pass`/`ticket`/`store` with `409 CHECKOUT_KIND_UNSUPPORTED`
   (`CheckoutKindUnsupportedException`, a documented ADR-23 safe-default) — for the *whole order* if
   any line has one of those kinds, not just the offending line. So the client-supplied-price spoofing
   risk (`CatalogPricingServiceAdapter.priceFromMetadata`) is **not** reachable through checkout today;
   it's a feature gap (those kinds can't be bought yet), not an open security hole. `POST /me/cart/items`
   itself has no such gate — it prices and adds any of the 7 kinds fine, so the existing store/events/
   podcasts "add to cart" buttons already work unchanged against the real backend. What's new: slice 3b
   must handle `409 CHECKOUT_KIND_UNSUPPORTED` on the "Pay" action with a specific, honest message
   ("some items in your cart can't be checked out yet") rather than a generic error, since a cart mixing
   a track with a ticket/store/episode/season-pass item will hit this today. Register `WU-COM-4` to add
   authoritative price-lookup ports against the now-shipped store/events/podcasts modules and remove the
   gate for those kinds — a feature-completion WU, not an urgent security patch — gated to land before
   Redde go-live, alongside the deferred card redirect.

## Scope

Two work units, two PRs:

- **3a (backend, `WU-COM-3`)** — `GET /v1/me/orders/{orderId}` + `order_line.subtitle`/`.image`
  columns (receipt fidelity — see Gap below).
- **3b (frontend)** — cart/checkout/receipt wiring, on branch `feat/frontend-commerce-checkout`.

`WU-COM-4` (authoritative pricing for episode/season-pass/ticket/store so checkout stops rejecting
them, + card hosted-checkout redirect) is registered in the backlog but
**not built** in this slice — it's a prerequisite for real-money go-live, not for wiring the mock away.

## Slice 3a — backend: order retrieval + receipt fidelity (`WU-COM-3`)

### `GET /v1/me/orders/{orderId}`

New `GetOrder` input port + `GetOrderService` + a new method on the existing `OrderResource`
(`commerce/adapter/in/rest/OrderResource.java`). Returns `OrderSnapshot` (existing shape, unchanged).
404s if the order doesn't belong to the caller — the same not-yours-is-404 convention already used
elsewhere in this module (§2.2), not a 403 (avoids confirming the order id exists to a caller who
doesn't own it). This is the polling target slice 3b uses for the pending→paid transition.

### Receipt fidelity gap: `order_line` is missing `subtitle`/`image`

`cart_item` stores `subtitle VARCHAR(256)` and `image VARCHAR(512)` (`V943` cart migration).
`order_line` (`V944__commerce_order_and_ownership.sql`) does not — it only carries
`id, order_id, kind, ref_id, title, unit_price_minor, currency, qty`. `CheckoutService` copies cart
lines into order lines at checkout time but silently drops subtitle/image because there's nowhere to
put them. `OrderLineView` (the wire shape) reflects this same gap.

The mock receipt (`checkout.complete.tsx`) renders both `item.image` and `item.subtitle` for every
line — a real order response can't satisfy that today. Fix: a forward-only migration adding
`order_line.subtitle` / `order_line.image` (nullable, same widths as `cart_item`), copied at checkout
alongside the existing `title`/`unit_price_minor` copy, and added to `OrderLineView`. This is additive
— no change to existing fields, no change to `OrderSnapshot`'s top-level shape.

### Tests

- Unit: `GetOrderServiceTest` (found + not-found + not-yours-404 cases).
- Integration: `OrderResource` single-order IT (200 with full shape including new fields; 404 for a
  foreign order).
- Contract: extend the existing commerce contract test to assert `subtitle`/`image` are
  present-but-nullable on order lines and don't break the existing list endpoint.

### Docs

`API-CONTRACT.md` (§6 Commerce: add the new endpoint row, add the two new fields to the
`OrderSnapshot`/line shape note) and `backend/docs/architecture/commerce.md` (as-built note), both in
the same PR — existing convention for every WU.

## Slice 3b — frontend: cart → checkout → receipt

### Cart: dual-mode `CartProvider`

`Frontend/src/features/cart/cart-context.tsx` is rewritten to run in two modes depending on
`useAuth().isAuthenticated`, mirroring the `CollectionProvider` pattern from slice 2b:

- **Logged out** — today's exact `useReducer` + `localStorage` implementation, byte-for-byte. No
  route guard is added; all 11 existing `addItem` call sites keep working unchanged.
- **Logged in** — backed by `useQuery(cartQuery())` over `GET /v1/me/cart`, with mutations
  (`addItem`/`setQuantity`/`removeItem`) as thin wrappers around `POST /me/cart/items`,
  `PATCH /me/cart/items/:lineId`, `DELETE /me/cart/items/:lineId`. Server responses (`CartView`) are
  the new cache value directly — no separate optimistic-transform layer for `addItem`, because
  `album-rest` is priced ownership-aware server-side and the client cannot know the correct amount in
  advance (guessing would flash a wrong price, worse than a brief loading state). `setQuantity` does
  get an optimistic local bump (the unit price is already known from the current cache), rolled back
  on error using the same `optimistic()` helper shape from `collection-context.tsx`.

**`useCart()`'s public shape is preserved**: `items`, `count`, `subtotal`, `fee`, `total`,
`addItem`/`removeItem`/`setQuantity`/`clear` keep their existing signatures, so `cart.tsx` and the
checkout page's order-summary panel render unchanged. The one signature change is `checkout()`, which
becomes `async () => Promise<string>` (resolves to the server's `orderId`) instead of a synchronous
fabricated reference.

**Cart item id → server request mapping.** The existing line-id convention already encodes
`kind:refId` (and a third segment for tickets): `track:last-last`, `album:foo`, `album-rest:foo`,
`episode:ep-1`, `season-pass:show-1`, `ticket:event-1:VIP`, `store:item-1`. `addItem` derives
`{kind, refId}` from this convention and forwards `title/subtitle/image` plus a `priceMinor` computed
from the existing `Money` field as `metadata` — exactly the shape `CatalogPricingServiceAdapter
.priceFromMetadata` already expects for the four non-catalog kinds. `track`/`album`/`album-rest` prices
are looked up authoritatively server-side regardless of what metadata is sent.

**Guest → auth merge.** On the `isAuthenticated` false→true transition (same `useRef`-guarded
transition pattern used for collection's logout cleanup in slice 2b, mirrored for login here), each
local cart line is POSTed to `/me/cart/items` in sequence, then `localStorage` is cleared and the
query is invalidated. Buy-once items (`track`/`album`/`episode`/`season-pass`) that 409/422 because
the account already owns them are silently dropped from the merge — added to cart before login,
already owned by the time they log in, nothing to buy. Stackable items merge by summing quantity
server-side (the existing `POST /items` add-with-existing-line behavior).

### Checkout: idempotent submit + async settlement poll

`routes/checkout.index.tsx`: `handlePay` becomes async. It generates one idempotency key (via `useRef`,
so a re-render or double-click reuses the same key rather than minting a new one — the same
double-submit-safety concern already solved for `AddCartItemsCommand` elsewhere), maps the picker's
selected method to the backend's provider wire values (`mtn`, `telecel`, `airtel`→`airteltigo`, `card`),
and calls `POST /v1/checkout` with `{ paymentMethodId }` and the `Idempotency-Key` header. On `202` it
navigates to `/checkout/complete?orderId=<id>` (the route gains an `orderId` search param). A `429`
surfaces as a toast using the response's `Retry-After` value instead of navigating. A
`409 CHECKOUT_KIND_UNSUPPORTED` (thrown today for the whole order if any line is `episode`/
`season-pass`/`ticket`/`store` — Decision 4) surfaces as a specific toast — "Some items in your cart
can't be checked out yet — remove them to continue" — instead of navigating or showing a generic
error.

`routes/checkout.complete.tsx` is rewritten around a `useQuery` polling `GET /me/orders/{orderId}`
(`refetchInterval: (query) => query.state.data?.status === 'pending' ? 2000 : false`):

- **`pending`** — new state: "Authorizing on your phone…" with the existing MoMo-PIN copy pattern
  from the checkout page, no receipt yet.
- **`paid` / `fulfilled`** — today's "Payment confirmed" receipt, unchanged markup, now populated from
  the real `OrderSnapshot` (including the new `subtitle`/`image` line fields from slice 3a).
- **`failed`** — new state: failure message + a link back to `/cart`.

On reaching a terminal `paid`/`fulfilled` status, the collection query (`COLLECTION_KEY`) is
invalidated so `ownedTracks` arrives from the server's next `GET /me/collection` fetch. The local
`markTracksOwned` call and its `useEffect` in `checkout.complete.tsks` are deleted entirely — ownership
now only ever comes from the server, consistent with INV-1.

No `orderId` param (e.g. a stale bookmark, or navigating here directly) falls back to today's
"No recent order" empty state.

### Files

- `Frontend/src/lib/api/queries/commerce.ts` (new) — `cartQuery()`, `ordersQuery(page)` (not used by
  this slice's UI but symmetrical with the resource), `orderQuery(orderId)`; pure transforms
  (`toCartItem`, `toCartView`, `toOrderSnapshot`) mapping wire `MoneyView`/`CartItemView`/
  `OrderLineView` → frontend `Money`/`CartItem`/order line shapes; API-call functions
  `apiGetCart`, `apiAddCartItem`, `apiUpdateCartItem`, `apiRemoveCartItem`, `apiCheckout`, `apiGetOrder`.
- `Frontend/src/lib/api/queries/commerce.test.ts` (new) — transform + API-call unit tests.
- `Frontend/src/features/cart/cart-context.tsx` — dual-mode rewrite as described above.
- `Frontend/src/routes/cart.tsx` — no markup change; confirm it still compiles against the preserved
  `useCart()` shape.
- `Frontend/src/routes/checkout.index.tsx` — async `handlePay`, idempotency key, provider mapping,
  429 toast.
- `Frontend/src/routes/checkout.complete.tsx` — rewritten around `orderId` search param + polling
  query; pending/failed states added; `markTracksOwned` call removed.

### Tests

- Vitest: id→`{kind, refId, metadata}` derivation, wire→app mappers, the merge-on-login line-by-line
  POST logic (mocked), optimistic quantity update + rollback.
- Live browser QA (the step that caught the IPv6 proxy bug and the `CancelledError` bug in prior
  slices, which unit tests did not): guest add-to-cart → log in → cart merges → checkout with MoMo →
  observe pending state → observe settlement (sandbox settles fast) → receipt renders with images →
  library shows newly owned tracks after a reload.

## Also in this slice

- Register `WU-COM-3` (`depends_on: [WU-COM-2]`) and `WU-COM-4` (`depends_on: [WU-COM-3, WU-STO-1,
  WU-EVT-1, WU-POD-1]`, not built here) in `backend/.project/backlog.yaml`.
- Close PR #125 (`feat/frontend-collection-library` → `master`, `+0 -0`, no files) — an accidental
  re-open of the already-merged slice 2b branch, discovered while scoping this slice.

## Explicitly out of scope

- Card hosted-checkout redirect (`checkoutUrl`) — `WU-COM-4`.
- Authoritative pricing for episode/season-pass/ticket/store (removing the `CHECKOUT_KIND_UNSUPPORTED`
  gate) — `WU-COM-4`.
- `GET /v1/me/orders` list view / order history page — the frontend has no such screen today; not
  invented here.
- Refund/dispute UI — no mock screen exists for it.
