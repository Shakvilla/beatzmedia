# BeatzClik — API Contract (v0.1, derived from the UI)

This document is the backend contract **reverse-engineered from the finished frontend**. Every
endpoint exists because a screen needs it; every response shape mirrors a TypeScript type in
`Frontend/src/types/index.ts` or a `lib/*-data.ts` model. The Quarkus `beatzmedia` service should
implement these; the frontend will swap its mock `getX()` functions for TanStack Query hooks that
call them, with **no change to the rendered UI**.

Status: **proposed** — nothing here is built yet. Shapes are authoritative (UI already depends on
them); paths/verbs are a recommendation.

---

## 1. Conventions

- **Base URL**: `https://api.beatzclik.com/v1`
- **Format**: JSON request/response, `Content-Type: application/json`, UTF-8.
- **Auth**: `Authorization: Bearer <jwt>`. Tokens carry `sub` (account id) and `roles`
  (`fan`, `artist`, plus admin roles `super-admin | finance | moderator | editor | support`).
- **IDs**: opaque strings (`ID`). Money is always `{ "amount": number, "currency": "GHS" }` in cedis.
- **Timestamps**: ISO-8601 strings. Durations are whole **seconds** (UI formats them).
- **Pagination**: list endpoints accept `?page=1&size=20`; respond with
  `{ "items": T[], "page": number, "size": number, "total": number }`.
- **Filtering/search**: documented per endpoint via query params (`?q=`, `?status=`, `?type=`, `?range=`).
- **Errors**: `{ "error": { "code": string, "message": string, "field"?: string } }` with standard
  HTTP status codes (400/401/403/404/409/422/429/500).
- **Writes are audited**: every privileged mutation (suspend, takedown, payout, config change…)
  must append an `AuditEntry` (see §13) with actor, action, target and reason.

---

## 2. Auth & accounts

UI: `login`, `signup`, header account menu, role gating, fan settings.

```
Account { id, name, email, avatar?: string|null, isArtist: boolean, isAdmin: boolean }
```

| Method | Path | Body / Notes | Returns |
|---|---|---|---|
| POST | `/auth/login` | `{ email, password }` | `{ token, account }` |
| POST | `/auth/signup` | `{ name, email, password }` → creates a **fan** | `{ token, account }` |
| POST | `/auth/social` | `{ provider: 'facebook'\|'google'\|'twitter', token }` | `{ token, account }` |
| POST | `/auth/logout` | — | `204` |
| GET | `/me` | current session | `Account` |
| POST | `/me/become-artist` | upgrades role to artist | `Account` |
| PATCH | `/me/settings` | fan prefs (theme, audio, notifications, country, phone) | `FanSettings` |
| POST | `/me/password/reset` | `{ email }` | `204` |

---

## 3. Catalog (read-mostly, public)

UI: home, search, artist, album, track, playlist pages. Shapes: `Artist`, `Album`, `Track`,
`Playlist`, `BrowseCategory`, `Show`, `Genre`.

| Method | Path | Notes | Returns |
|---|---|---|---|
| GET | `/home` | curated feed (trending, top-10, featured albums, discover rails) | `{ trending: Track[], top10: Track[], featuredAlbums: Album[], rails: {...} }` |
| GET | `/search?q=` | tracks/artists/albums/playlists + a top result | `{ tracks, artists, albums, playlists }` |
| GET | `/browse-categories` | search-screen tiles | `BrowseCategory[]` |
| GET | `/artists/:id` | profile + hero | `Artist` |
| GET | `/artists/:id/tracks` | popular tracks | `Track[]` |
| GET | `/artists/:id/albums` | discography | `Album[]` |
| GET | `/artists/:id/shows` | upcoming shows | `Show[]` |
| GET | `/albums/:id` | + `?tracks=true` to embed | `Album` (+ `tracks: Track[]`) |
| GET | `/tracks/:id` | track detail (credits, lyrics ref) | `Track` |
| GET | `/tracks/:id/lyrics` | timed lyrics | `{ lines: { time, text }[] }` |
| GET | `/playlists/:id` | + tracks | `Playlist` (+ `tracks: Track[]`) |

---

## 4. Playback & streaming

UI: global player, 30s preview gate on for-sale tracks.

| Method | Path | Notes | Returns |
|---|---|---|---|
| GET | `/tracks/:id/stream` | returns a signed, time-boxed audio URL; **server decides preview vs full** based on ownership and returns `previewSeconds` when gated | `{ audioUrl, previewSeconds?: number, expiresAt }` |
| POST | `/tracks/:id/play` | record a play (for plays count / royalties) | `204` |

> The UI's `ownership` field and 30s preview cap come from here: the server returns a full URL for
> free/owned tracks and a preview URL + `previewSeconds` for for-sale tracks the caller doesn't own.

---

## 5. Library & collection (per-user)

UI: Library, like/follow/save buttons, user playlists, owned tracks. Backs the `collection` store.

```
Collection { likedTracks: ID[], followedArtists: ID[], followedPlaylists: ID[],
             followedShows: ID[], savedAlbums: ID[], ownedTracks: ID[], userPlaylists: UserPlaylist[] }
```

| Method | Path | Notes |
|---|---|---|
| GET | `/me/collection` | everything above |
| PUT/DELETE | `/me/likes/tracks/:id` | like / unlike |
| PUT/DELETE | `/me/follows/artists/:id` | follow / unfollow (also playlists, shows) |
| PUT/DELETE | `/me/saved/albums/:id` | save / unsave |
| GET/POST | `/me/playlists` | list / create `{ title, firstTrackId? }` → `UserPlaylist` |
| PATCH/DELETE | `/me/playlists/:id` | rename / delete |
| PUT/DELETE | `/me/playlists/:id/tracks/:trackId` | add / remove track |
| GET | `/me/owned` | owned track ids (drives playback unlock) |

---

## 6. Commerce (cart, checkout, orders)

UI: cart, checkout, receipt, buy buttons, buy-to-own unlock. Shapes: `CartItem`, `OrderSnapshot`.
Cart kinds: `track | album | album-rest | store | episode | season-pass | ticket`.

| Method | Path | Body / Notes | Returns |
|---|---|---|---|
| GET | `/me/cart` | — | `{ items: CartItem[], subtotal, fee, total, count }` |
| POST | `/me/cart/items` | `{ id, kind, refId, qty?, metadata? }` (digital goods are buy-once) | cart |
| PATCH/DELETE | `/me/cart/items/:lineId` | set qty / remove | cart |
| POST | `/checkout` | `{ paymentMethodId }` → charges MoMo/card, **grants ownership**, returns receipt | `OrderSnapshot` |
| GET | `/me/orders` | purchase history | `OrderSnapshot[]` |

> `/checkout` is what unlocks tracks: on success the server adds the purchased track ids (and all
> tracks of a purchased album) to the user's owned set, so playback stops being preview-gated.
>
> **Payment gateway (WU-PAY-6, backend-only for now).** MoMo/card charges are processed by a pluggable
> PSP (Redde), toggled server-side via the `PSP_REDDE` feature flag; the sandbox stands in until real
> credentials are supplied. Two additive, backward-compatible surfaces were introduced and are **not yet
> consumed by the frontend** (forward-compatible):
> - The internal payment-intent response gains an optional `checkoutUrl?: string | null` — non-null only
>   for a **card** charge that requires a hosted-checkout redirect (Redde has no server-side card API).
>   The client redirects the browser to it; ownership is never granted off that redirect — settlement is
>   confirmed server-side (ADR-28). `null` for MoMo/sandbox charges.
> - Provider settlement callbacks: `POST /v1/payments/webhooks/redde/receive` (unauthenticated; trusted by
>   an authenticated pull-back, not a signature). Not a client-facing endpoint.

---

## 7. Store (marketplace)

UI: store overview + tabs (beats, hi-fi, merch, exclusives) + product detail. Shapes: `StoreItem`,
`LicenseOption`, `MerchVariant`, `StoreSort`.

| Method | Path | Notes |
|---|---|---|
| GET | `/store?type=&genre=&sort=popular\|newest\|price-asc\|price-desc` | filtered catalog |
| GET | `/store/:id` | product detail (license tiers / variants / drop date / stock) |

---

## 8. Podcasts

UI: podcasts list, show detail, support modal. Shapes: `Podcast`, `PodcastEpisode`.

| Method | Path | Notes |
|---|---|---|
| GET | `/podcasts` | shows (+ `?category=`) |
| GET | `/podcasts/:id` | show detail |
| GET | `/podcasts/:id/episodes` | episodes (premium/early-access flags + ownership) |
| POST | `/podcasts/:id/tip` | `{ amount }` instant MoMo tip |

---

## 9. Events & ticketing

UI: events list, event detail, ticket tiers. Shapes: `Event`, `TicketTier`.

| Method | Path | Notes |
|---|---|---|
| GET | `/events?city=&category=` | listings |
| GET | `/events/:id` | detail + `ticketTiers` |
| (tickets are bought via §6 cart, `kind: 'ticket'`, `refId: eventId:tier`) | | |

---

## 10. Notifications

UI: header bell + `/notifications`. Shape: `AppNotification { id, type, title, body, time, read, to? }`.

| Method | Path | Notes |
|---|---|---|
| GET | `/me/notifications` | list + unread count |
| POST | `/me/notifications/read` | mark all read |
| POST | `/me/notifications/:id/read` | mark one read |

---

## 11. Studio (creator) — requires `artist` role

UI: studio overview, releases, 4-step release wizard, analytics, audience, payouts, profile, settings.

### Profile
`StudioProfile { displayName, username, hometown, genres[], bio, avatar, banner, links{instagram,twitter,youtube,website}, shows[], featuredTrackId, bookingEmail, pressAssets[] }`

| GET/PUT | `/studio/profile` | read / save |

### Releases (catalog the artist owns)
`StudioRelease { id, title, type: single|ep|album|mixtape, status: live|scheduled|in_review|draft, date, trackCount, streams, revenue, price }`

| Method | Path | Notes |
|---|---|---|
| GET | `/studio/releases` | list (status filter) |
| POST | `/studio/releases` | **wizard submit** — full draft (details + tracks + splits + pricing) → creates `in_review` release |
| GET/PATCH/DELETE | `/studio/releases/:id` | manage (edit metadata, publish/unpublish, delete) |
| POST | `/studio/releases/:id/tracks` | multipart audio upload (WAV/FLAC) → returns track w/ duration, status |
| Draft sub-shapes | — | `UploadedTrack`, `SplitEntry[]` per track (collaborator, role, percent, confirmation) |

### Podcasts (creator)
`StudioPodcastShow { id, title, category }`
`StudioEpisode { id, showId, showTitle, title, duration, status: published|scheduled|draft, premium, price, publishedAt, plays }`

| Method | Path | Notes |
|---|---|---|
| GET | `/studio/podcasts/shows` | the creator's shows |
| POST | `/studio/podcasts/shows` | `{ title, category }` → `StudioPodcastShow` |
| GET | `/studio/podcasts/episodes` | the creator's episodes (+ plays, status) |
| POST | `/studio/podcasts/episodes` | **new-episode upload** — multipart audio + `{ showId\|newShow, title, description, cover?, visibility: public\|scheduled, date?, premium, price?, earlyAccess? }` → `StudioEpisode` |
| PATCH/DELETE | `/studio/podcasts/episodes/:id` | edit metadata / delete |

> Mirrors the music pipeline: free / premium (buy-to-own) / early-access episodes, 70% creator
> share, publish-now or schedule. Feeds the fan-side `Podcast` / `PodcastEpisode` shapes in §8.

### Insights (read, `?range=7d|28d|90d|12m|all`)
| GET | `/studio/analytics` | KPIs + per-metric series (streams/sales/followers/tips) + top tracks + countries + revenue split + sources + engagement |
| GET | `/studio/audience` | listeners, followers, superfans, cities, gender, age, top fans |

### Payouts
`{ available, pending, thisMonth, lifetime, methods: PayoutMethod[], transactions: PayoutTxn[] }`
| GET | `/studio/payouts` | balance + ledger + methods |
| POST | `/studio/payouts/withdraw` | `{ amount, methodId }` → cash-out (fee + arrival from server) |
| POST/DELETE | `/studio/payout-methods` | add / remove; `PATCH …/default` |

> **WU-PAY-7 (additive).** The add body carries structured destination fields the real payout rail
> (Redde `/v1/cashout`) needs, alongside `{ label, detail, kind }`. For `kind:"momo"`:
> `{ network: "mtn"|"telecel"|"airteltigo", walletNumber }`. For `kind:"bank"`:
> `{ bankCode, bankName, accountName, accountNumber }` (`bankCode` is a validated Ghana bank token;
> an unknown code or a missing required field is a `422`). The unused subset for a kind may be omitted.
> The `PayoutMethod` read model is **unchanged** (no raw wallet/account numbers are returned — the
> masked `detail` remains the display value). Real disbursement is a deploy-secret human gate; with it
> off the sandbox pays exactly as before.

### Studio settings
| GET/PUT | `/studio/settings` | notifications, sales defaults, payouts, privacy, team, security |

---

## 12. Admin (platform) — requires an admin role; **scope per §14**

### Overview / health
| GET | `/admin/overview?range=24h\|7d\|30d` | KPIs, GMV series, needs-attention, top artists, payment mix |
| GET | `/admin/health` | status, metrics, concurrent-listeners series, incidents |

### Users
| GET | `/admin/users?q=&filter=fans\|artists\|verified\|suspended&page=&size=` | paged list + counts |
| GET | `/admin/users/:id` | detail (activity, orders, devices, action log) |
| POST | `/admin/users/:id/verify` | verify artist |
| POST | `/admin/users/:id/suspend` | `{ reason }` (required, audited) |
| POST | `/admin/users/:id/reactivate` | — |
| POST | `/admin/users/:id/impersonate` | returns scoped session |
| POST | `/admin/users/:id/data-export` | GDPR export job |

### Catalog moderation
| GET | `/admin/catalog?status=pending\|published\|takedown&q=&page=` | + summary counts |
| GET | `/admin/catalog/:id` | tracklist, ISRC/UPC, rights/splits, action log |
| POST | `/admin/catalog/:id/{approve\|flag\|takedown}` | takedown requires `{ reason }` |

### Moderation queue
| GET | `/admin/moderation?status=&type=` | queue + SLA/escalation summary |
| POST | `/admin/moderation/:id/{review\|approve\|remove\|escalate\|dismiss}` | |

### Finance
| GET | `/admin/finance?range=` | GMV, fees, payouts due, MoMo float, provider mix, pending payouts, disputes |
| POST | `/admin/finance/payouts/run-weekly` | batch pay all ready |
| POST | `/admin/finance/payouts/:id/send` | single (blocks on KYC) |
| GET | `/admin/finance/ledger?type=&q=&page=` | transaction ledger |
| GET | `/admin/finance/disputes/:id` | detail + timeline |
| POST | `/admin/finance/disputes/:id/{refund\|reject\|escalate}` | refund logs amount |

### Editorial
| GET/PUT | `/admin/editorial/featured` | ordered home-featured slots |
| GET/POST | `/admin/editorial/push` | scheduled push notifications |
| GET/POST | `/admin/editorial/playlists` | curated playlists |

### Trust & safety
| GET | `/admin/risk` | fraud KPIs + risk signals |
| POST | `/admin/risk/:id/{review\|clear\|ban}` | |

### Support
| GET | `/admin/support/tickets?status=&q=` | inbox |
| GET | `/admin/support/tickets/:id` | thread |
| POST | `/admin/support/tickets/:id/reply` | `{ text }` |
| POST | `/admin/support/tickets/:id/{assign\|resolve}` | |

### Compliance
| GET | `/admin/compliance?type=DSAR\|Takedown\|Tax` | requests + due dates |
| POST | `/admin/compliance/:id/{start\|complete}` | advance status |
| POST | `/admin/compliance/:id/{export\|notice}` | DSAR data / DMCA notice |

---

## 13. Audit log

UI: `/admin/audit`. `AuditEntry { id, actor, action, target, type, time }`.

| GET | `/admin/audit?type=&q=&page=` | paged trail (write side is automatic — every mutation logs) |

---

## 14. Admin RBAC & platform settings

UI: `/admin/settings`. Roles: `super-admin | finance | moderator | editor | support`.

```
PlatformSettings { platformFeePct, payoutDay, payoutMinimum, defaultCurrency,
                   maintenanceMode, providers{momo,vodafone,airteltigo,card,bank},
                   flags{artistSignups,podcasts,events,tipping,fanMessaging} }
AdminMember { id, name, email, role, lastActive }
```

| Method | Path | Notes |
|---|---|---|
| GET/PUT | `/admin/settings` | platform config (super-admin only) |
| GET | `/admin/team` | admin members |
| POST | `/admin/team/invite` | `{ email, role }` |
| PATCH/DELETE | `/admin/team/:id` | change role / remove |

**Role scopes** (enforced server-side, mirror the UI's permission reference):
super-admin = all · finance = payouts/ledger/disputes · moderator = moderation/takedowns ·
editor = editorial · support = user lookup + read-only elsewhere.

---

## 15. Frontend migration notes

- Each mock `getX()` in `Frontend/src/lib/*` maps 1:1 to a GET above; replace with a TanStack Query
  hook (`useQuery`) keyed by resource + params. Mutations (`addRelease`, `withdraw`, `suspend`,
  toggles…) become `useMutation` with optimistic updates + cache invalidation.
- Client stores that currently persist to `localStorage` (auth, collection, cart, studio,
  notifications) become **server-owned**; keep the same context APIs as a thin cache so call sites
  don't change.
- `localStorage` persistence is a stand-in for the DB and should be removed once these land.
- Money/duration stay structured; the server must not pre-format display strings.
