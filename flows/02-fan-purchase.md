# Flow 02 — Fan: browse → cart → checkout → payment

The core flow of a buy-to-own marketplace. Run as `kofi.fan.qa@beatzclik.local`, a fresh
account owning nothing.

Related issues: [I-2](ISSUES.md#i-2), [I-4](ISSUES.md#i-4), [I-6](ISSUES.md#i-6).

---

## 2.1 Page sweep

Every fan-facing page was loaded directly and checked for render failures.

| Route | Result | Notes |
|---|---|---|
| `/` | **Pass** | Greeting, featured albums, "Trending in Ghana", playlists |
| `/library` | **Pass** | Honest empties: "Liked Songs 0 songs", "Owned Tracks 0 tracks" |
| `/search` | **Pass** | Genre browse tiles |
| `/store` | **Pass** | 33 products, category tabs, sort control |
| `/store/:id` | **Pass** | Detail with price and **Add to cart** |
| `/podcasts` | **Pass** | Featured show, categories |
| `/events` | **Pass** | Event cards with dates and venues |
| `/notifications` | **Pass** | "No notifications yet." |
| `/settings` | **Fail** | Hardcoded spend figure — [I-6](ISSUES.md#i-6) |
| `/cart` | **Pass** | Correct empty state |
| `/checkout` | **Pass** | Correct empty state ("Nothing to check out") |

No blank pages, no error boundaries, no console errors during the sweep.

---

## 2.2 Add to cart

| | |
|---|---|
| **Steps** | `/store` → "VIP Meet & Greet — Accra" → **Add to cart** |
| **Expected** | Item persisted server-side, cart badge updates |
| **Actual** | `POST /v1/me/cart/items → 200`. Badge shows **1**. Item survives a page reload, so it is genuinely persisted, not local state |
| **Result** | **Pass** |

---

## 2.3 Cart totals

| | |
|---|---|
| **Actual** | Items (1) ₵800.00 · Service fee ₵0.50 · **TOTAL ₵800.50** |
| **Result** | **Pass** |

The ₵0.50 service fee matches the documented platform constant. Arithmetic is correct.

---

## 2.4 Checkout screen — **FAIL**

| | |
|---|---|
| **Expected** | Payment methods belonging to this account; none saved for a new user |
| **Actual** | Shows **"MTN MoMo · 0244 ••• 9210 - default"** as a saved method, plus body copy naming that same number. This account has never added a payment method |
| **Result** | **Fail** — see [I-4](ISSUES.md#i-4) |

Order summary itself is correct: line item, subtotal ₵800.00, fee ₵0.50, total ₵800.50.
Alternative methods (Telecel Cash, AirtelTigo Money, Card) are listed with "Add new number".

---

## 2.5 Payment — **FAIL**

| | |
|---|---|
| **Steps** | Click **Pay ₵800.50 with MoMo** |
| **Expected** | Order created, then a bounded wait with a clear outcome |
| **Actual** | Order created correctly — `POST /v1/checkout → 202 Accepted`, redirect to `/checkout/complete?orderId=…`. Then the page sits on *"Authorizing on your phone…"* **forever** |
| **Result** | **Fail** — see [I-2](ISSUES.md#i-2) |

Observed **30+ polls of `GET /v1/me/orders/:id` over ~60 seconds**, every 2 seconds, with no
UI change and no end. No cancel, no timeout, no "still waiting" copy. The cart badge still
reads **1** throughout, so the item remains in the cart while an order is in flight.

The order itself is recorded correctly server-side:

```json
{ "createdAt":"2026-07-31T23:30:10Z",
  "fee":{"amount":0.50,"currency":"GHS"},
  "items":[{"kind":"store","refId":"exclusive-meet-greet",
            "title":"VIP Meet & Greet — Accra","unitPrice":{"amount":800.00,"currency":"GHS"}}] }
```

So this is **not** a broken backend — the order is real and pending. The gap is that the UI
has no answer for a payment that never confirms, which is the normal outcome of an expired
or declined MoMo prompt.

---

## 2.6 Ownership after purchase — not reachable

Could not be tested. Ownership is granted only on confirmed payment settlement, and no
payment provider is wired in dev, so the order cannot reach `paid`. To test this end-to-end
the team needs either a provider sandbox or a dev-only settlement hook.

**This means the single most important promise of the product — "buy once, own forever" —
is currently unverifiable through the UI.** Recommend prioritising a way to exercise it.

---

## Summary

The purchase path is **correct up to the moment of payment**: real persistence, correct
money maths, a real order. It fails at the two points where the user is most exposed —
being shown a payment method that is not theirs, and being stranded with no idea whether
they have been charged.
