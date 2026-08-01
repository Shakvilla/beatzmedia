# Flow 06 — The First-Time Fan

Written from the seat of a new user. I created a brand-new account
(`adjoa.newfan@beatzclik.local`, "Adjoa Bediako") with an empty session and explored the way
someone would who had just heard about BeatzClik. Everything below is what I actually saw,
in the order I saw it.

**Bottom line:** I signed up for a music app, pressed play, and heard nothing. Then I clicked
a song to buy it and was told the song does not exist. Those two things happen within the
first two minutes.

---

## Act 1 — Arriving

### I cannot see anything before signing up

I opened `http://localhost:5173/` and was sent straight to `/login`. So I tried the things a
curious person would try:

| I tried | I got |
|---|---|
| `/store` | → `/login` |
| `/search` | → `/login` |
| `/podcasts` | → `/login` |
| `/events` | → `/login` |
| `/artist/rema` | → `/login` |
| `/track/last-last` | → `/login` |
| `/album/iron-boy` | → `/login` |

**Every single route is behind the wall.** Not one song, artist page, or price is visible
until I hand over an email address.

**Why this hurts.** If an artist shares their track link in a WhatsApp group — the single most
likely way this platform grows in Ghana — everyone who taps it hits a login form instead of the
music. There is no preview, no "listen to 30 seconds", no reason to sign up yet. The product
asks for commitment before it has shown me anything.

→ [I-21](ISSUES.md#i-21)

### Signing up was fast, and then nobody asked me anything

Name, email, password, **Get started** — I was in, in about two seconds. That part is good.

But there was **no onboarding at all**. No "pick some genres", no "follow a few artists", no
"what do you listen to?". I landed on a fully-populated home page that knows nothing about me.

→ [I-22](ISSUES.md#i-22)

### The home page immediately claims to know my taste

Two things on the first screen are not true:

1. A section headed **"Made for you — Mixes and playlists picked for your taste."** I had
   existed for three seconds. It cannot have been picked for my taste.
2. The player bar at the bottom was **already loaded with "Last Last" by Burna Boy**, with a
   queue behind it. I never chose that song. It looks like something is playing, or about to.

→ [I-24](ISSUES.md#i-24)

### Broken images, straight away

**8 of the 49 images on the home page fail to load.** Some are remote artwork, but several
fall back to `/images/placeholder.jpg` — and that file does not exist. The dev server answers
it with the app's own HTML page (`content-type: text/html`), so the fallback is broken too and
those tiles render empty.

→ [I-23](ISSUES.md#i-23)

---

## Act 2 — Trying to listen

### Nothing plays. Ever.

I clicked the big green **Play album** on the featured album. The interface responded
perfectly: the player switched to **"NOW PLAYING — Iron Boy (intro), Black Sherif"**, the
queue filled with the rest of the album, and the counter started running — I watched it go
**0:00 → 0:02** against a 1:12 duration.

**There was no sound, because there is no audio.** There is no `<audio>` element anywhere in
the app, before or after pressing play. The progress bar is a timer counting up next to
silence.

This is the whole point of the product, and every play button across Home, Search, Library,
Album, Artist, Store and Playlist is affected.

→ [I-12](ISSUES.md#i-12)

### Search is genuinely good

The one part of discovery that behaved exactly as I hoped. I typed "black sherif" and got
live results with a **Top result** card, songs with prices, artists and albums.

The filter tabs work properly:

| Tab | Results |
|---|---|
| All | 9 (mixed: artists, tracks, albums) |
| Tracks | 9 |
| Artists | 1 |
| Albums | 2 |
| Playlists | 0 |

One rough edge: **the Playlists tab returns nothing and says nothing.** Just empty space, no
"no playlists match". For a second I thought the page had broken.

→ [I-26](ISSUES.md#i-26)

---

## Act 3 — Trying to buy a song

This is the promise on the tin: *Buy. Own. Support.* Here is where it fell apart.

### Clicking a song from search says the song does not exist

I searched, saw **"For My Hand" — ₵3.00**, and clicked it.

> ## Track not found
> *Back to home*

I assumed I had mistyped something. I had not. The track is real:

```
GET /v1/tracks/for-my-hand          → 200 OK      ← the track loads fine
GET /v1/tracks/for-my-hand/lyrics   → 404 Not Found
```

**A missing lyrics file takes down the entire track page.** The 404 from the lyrics request is
being treated as "this track does not exist".

I checked the rest of the catalogue:

| Track | Track API | Lyrics API | Page |
|---|---|---|---|
| Last Last | 200 | **200** | ✅ loads |
| For My Hand | 200 | 404 | ❌ "Track not found" |
| It's Plenty | 200 | 404 | ❌ "Track not found" |
| Calm Down | 200 | 404 | ❌ "Track not found" |

**Exactly one song in the entire catalogue has lyrics, so exactly one song has a working
detail page.** Every other song I click — from search, from an album, from a playlist — tells
me it does not exist.

→ [I-20](ISSUES.md#i-20)

### The album page told me I own music I have never bought

I went round the back way, via the album. `/album/iron-boy` loads, and it says:

> **YOU OWN 2/6** · 1 free track included

I created this account four minutes ago. I have bought nothing. The track list shows
"Hold On" and "Akwasidae" as **OWNED**.

The API confirms the contradiction on the same account:

```
GET /v1/me/collection                → { "ownedTracks": [] }          ← nothing
GET /v1/albums/iron-boy?tracks=true  → "Hold On"  ownership:"owned"   ← something
                                       "Akwasidae" ownership:"owned"
```

Note the intro track comes back as `ownership: "free"` — so **a `free` state exists and is
used**. These two tracks simply have no price and get labelled `owned` instead of `free`.

Meanwhile `/library` correctly says **"Owned Tracks · 0 tracks"**. So the app contradicts
itself about the single thing it is built to track.

→ [I-13](ISSUES.md#i-13)

### Buying a track from the album worked

Clicking **Buy** on a priced track did exactly the right thing:
`POST /v1/me/cart/items`, and a clear confirmation — *"Konongo Zongo II" added to cart*.

### The album pricing punishes me for already owning some of it

The album page offers two buttons side by side:

> **Buy rest • ₵7.50**  |  **Buy album • ₵6.00**

Completing my collection costs **₵1.50 more** than buying the whole album from scratch. The
bundle discount applies to the full album but not to the remainder, so the more of an album a
fan owns, the worse their deal gets. As a customer that feels like a penalty for loyalty.

→ [I-25](ISSUES.md#i-25)

### Checkout looks right, then strands me

Cart maths was correct throughout (item + ₵0.50 service fee). Checkout offered MoMo, Telecel,
AirtelTigo and Card.

But the MoMo option was pre-filled with **"0244 ••• 9210 - default"** — a number I have never
given this app — and the confirmation copy names it again ([I-4](ISSUES.md#i-4)).

Pressing **Pay** created a real order and then left me on *"Authorizing on your phone…"*
**forever** — no timeout, no cancel, no failure message, with my item still sitting in the cart
([I-2](ISSUES.md#i-2)). I never found out whether I had been charged.

---

## Act 4 — Collecting

The good news: this is the most solid part of the app. Everything here did what it said.

| What I did | Result |
|---|---|
| Liked a song | ✅ saved (`POST /v1/me/likes/tracks/…`) |
| Followed an artist | ✅ saved (`POST /v1/me/follows/artists/…`) |
| Created a playlist | ✅ created and opened it |
| Added a song to that playlist | ✅ saved via the picker |
| Changed cart quantity | ✅ `PATCH` |
| Removed a cart item | ✅ `DELETE` |
| Library | ✅ honest empty states, "0 songs", "0 tracks" |
| Notifications | ✅ honest "No notifications yet." |
| Light/dark theme toggle | ✅ works |

---

## Act 5 — Podcasts and events

**Podcasts are in good shape.** The show page offered **Buy pass • ₵12.00** for the whole show
and **Buy ₵3.00** on individual premium episodes, with free episodes playable (in the sense
that nothing plays anywhere). Buying a pass correctly added it to my cart.

**Events work too.** Ticket tiers were priced and described (Entry ₵80.00, Booth (4) ₵1500.00
with bottle service), and **Buy with MoMo** added the ticket to my cart and took me there.

**Tipping does not work, and lies about it.** On the artist page I clicked **₵ Tip artist**,
chose ₵5, and hit **Send ₵5 with MoMo**. I got a warm green confirmation:

> *Thank you! ₵5 tipped to Burna Boy via MoMo 💚*

**No request was made. No money moved. Burna Boy will never know.** I instrumented the network
during the click and recorded zero calls. The same applied to "Support the show" on podcasts.

*Since fixed:* both now say "Tipping isn't available yet — no payment was made." The feature
itself remains blocked on the two contract gaps described in [I-14](ISSUES.md#i-14).

---

## Act 6 — My account

`/settings` shows my name and email correctly, and then this row:

> **0** OWNED  ·  **0** PLAYLISTS  ·  **₵312** SPENT

I have spent nothing. I have owned nothing. The ₵312 is hardcoded into the page and shown to
every user ([I-6](ISSUES.md#i-6)). The row contradicts itself two columns apart.

(By this point I *had* created a playlist, so "0 PLAYLISTS" was also stale.)

Also on this page: **Edit profile**, **Upgrade · ₵40/mo** and **Clear cache** are all
decorative — they toast and do nothing.

---

## How it felt, in one paragraph

The app looks finished. That is the problem. The visual design, empty states, pricing
displays, cart arithmetic and collection features are genuinely good — good enough that I
trusted the parts that were lying to me. I believed a song was playing because the timer moved.
I believed I owned two tracks because the album said so. I believed I had tipped an artist
₵5 because it thanked me. A rougher-looking app would have made me suspicious; this one did
not, and that is what makes the fabricated states dangerous rather than merely broken.

## If the team fixes five things, fix these

1. **Track pages** — one missing lyrics file should not 404 an entire song
   ([I-20](ISSUES.md#i-20)). This is the highest ratio of damage to effort in the whole list.
2. **Audio** — the product does not currently play music ([I-12](ISSUES.md#i-12)).
3. **Tipping** — the toast no longer claims money moved (fixed), but the feature is still
   unavailable. `POST /v1/payments/tips` and `POST /v1/podcasts/:id/tip` exist, yet neither is
   callable from the app yet: they need a provider `paymentToken` the client cannot obtain, and
   the artist path also needs a creator **account** id that artist pages do not carry. Both are
   contract gaps to close before wiring ([I-14](ISSUES.md#i-14)).
4. **Ownership** — use the `free` state that already exists, so the app stops telling fans
   they own things they have not bought ([I-13](ISSUES.md#i-13)).
5. **Checkout** — give a stranded payment somewhere to go ([I-2](ISSUES.md#i-2)).

Then, before launch, let people look around without an account
([I-21](ISSUES.md#i-21)) — nothing else on this list matters if nobody gets past the login form.
