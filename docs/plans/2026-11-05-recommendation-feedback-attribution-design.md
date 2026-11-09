# Recommendation Feedback and Attribution Design

**Status:** Approved for implementation
**Date:** 2026-11-05
**Work unit:** WU-DIS-3

## 1. Outcome

Add a measurable improvement loop to the authenticated **For You** rail. Fans can reject a specific
recommendation or ask BeatzClik not to recommend an artist or podcast show again. Discovery records
privacy-safe impressions and opens, and engagement connects an attributed recommendation to an
authoritative listening outcome.

The work keeps personalized recommendations request-time and bounded. It does not persist complete
ranked feeds, raw ranking signals, or recommendation scores.

## 2. Product behavior

Each recommendation card exposes an accessible overflow menu with:

- **Not interested**, which suppresses that single, album, EP, or podcast show;
- **Don't recommend this artist** for music recommendations;
- **Don't recommend this podcast** for podcast-show recommendations.

The source-level action is recommendation-only. It does not unfollow a source, remove owned content,
prevent playback, hide direct links, alter Search or Following, or act as a moderation report. Music
suppression targets the artist. Podcast suppression targets the show rather than the publishing
account.

Feedback removes the card optimistically, refreshes the rail, and fills the vacated position with the
next eligible candidate. Undo removes the suppression and refreshes the rail. Failed feedback rolls
the optimistic change back and presents a retryable error.

## 3. Ownership and boundaries

`discovery` owns:

- short-lived recommendation attribution records;
- idempotent impression and open facts;
- durable explicit recommendation suppressions;
- suppression filtering before ranking and type reservation;
- ranking-version and reason-code metadata.

`engagement` remains authoritative for starts, qualified plays, completions, skips, repeats, and
listened duration. It accepts an optional recommendation reference and reports privacy-safe attributed
outcomes without making playback depend on Discovery availability.

Catalog, podcasts, library, playback, commerce, and analytics retain their existing authority.
Creators never receive account-level recommendation activity or feedback. Future creator reporting
may expose only aggregated metrics.

## 4. API contract

`GET /v1/me/recommendations?limit=` retains its existing response and adds to every item:

- `recommendationId`: opaque, unguessable, and bound to the JWT subject;
- `rankingVersion`: the ruleset used to rank the item;
- `position`: the one-based position returned to the fan.

`POST /v1/me/recommendation-events` accepts bounded, idempotent event batches. Initial event types
are `impression` and `open`. Each event carries a client-generated event ID and a recommendation ID.
The backend derives content, source, surface, version, and position from the attribution record rather
than trusting client-supplied metadata.

`POST /v1/me/recommendation-feedback` accepts a recommendation ID and one action:

- `not-interested`;
- `dont-recommend-source`.

It creates or returns an idempotent suppression and responds with its opaque suppression ID.
`DELETE /v1/me/recommendation-suppressions/{suppressionId}` provides Undo. A missing, expired, or
cross-account identifier returns `404` without revealing whether another account owns it.

The first matching listening checkpoint may carry `recommendationId`. Invalid or unavailable
attribution is ignored for attribution purposes and cannot reject or interrupt listening.

## 5. Persistence and retention

Discovery stores three concepts.

### 5.1 Recommendation attribution

One short-lived row per returned card contains:

- recommendation ID and account ID;
- content kind and ID;
- source type and ID;
- surface, reason code, ranking version, and position;
- creation and expiry timestamps.

It does not contain scores, raw history, affinity inputs, IP addresses, or device fingerprints.

### 5.2 Recommendation event

An immutable, idempotent fact contains the client event ID, recommendation ID, event type, and server
receipt timestamp. Event IDs are unique. Repeated delivery is a no-op and cannot inflate metrics.

### 5.3 Recommendation suppression

A durable preference contains account ID, target type, target kind, target ID, creation timestamp,
and an opaque suppression ID. `(account_id, target_type, target_kind, target_id)` is unique.
Suppressions remain until Undo or account deletion.

Attribution and raw recommendation-event rows expire after 90 days. A scheduled bounded cleanup
removes expired rows. Explicit suppressions are independent of listening-history collection: history
opt-out or clearing history does not erase deliberate recommendation preferences.

## 6. Ranking and read behavior

Candidate collection remains bounded. Discovery asks its repository only for suppressions matching
the current candidate content and source IDs instead of loading an account's full suppression history.
Filtering occurs before deduplication, content-type reservation, and final scoring so hidden targets do
not consume positions or reduce the requested result size when eligible replacements exist.

Attribution-write failure must not make the recommendation endpoint unavailable. Items that cannot
receive an attribution identity may still be returned without feedback controls and are not counted as
measurable recommendations. The failure is observable for operators.

## 7. Frontend and attribution flow

An impression is eligible only after at least half of a card is visible for approximately one second.
The web client batches best-effort impression events. An open event is sent without delaying
navigation.

The selected recommendation is held in a small expiring session-storage context rather than placed in
the URL. A direct single play matches its track. An album or EP recommendation matches the first
played track belonging to that release. A podcast-show recommendation matches the first played
episode belonging to that show. The matching recommendation ID is attached to the first listening
checkpoint and then consumed.

Engagement validates the account-bound reference through a narrow Discovery input port. It retains
the association needed to attribute later qualified-play and completion facts. A validation or
Discovery outage produces unattributed listening, never a playback failure.

## 8. Failure behavior

- Impression and open delivery are asynchronous, retryable, and non-blocking.
- Duplicate events are safe no-ops.
- Feedback failure rolls back the optimistic UI and exposes retry.
- Suppression write and Undo are transactional and account-isolated.
- Suppression-read failure is observable and cannot be treated as a successfully applied preference.
- Expired or cross-account recommendation IDs do not leak record existence.
- Cleanup runs in bounded batches and safely resumes after failure.
- Attribution cannot grant ownership, affect royalties, or become financial evidence.

## 9. Testing

Backend unit and integration tests cover:

- content and source suppression with replacement candidates;
- filtering before four-type reservation;
- idempotent feedback, Undo, and event ingestion;
- account isolation and opaque-identifier handling;
- attribution expiry and scheduled cleanup;
- history-disabled accounts retaining explicit suppressions;
- album-to-track and podcast-to-episode attribution;
- attribution failure leaving engagement and playback available.

Frontend tests cover:

- the accessible action menu;
- the visibility threshold and event batching;
- open tracking without navigation delay;
- optimistic removal, Undo, rollback, and retry;
- expiring navigation context and matching content inheritance.

One integration journey proves recommendation generation, impression, open, attributed start, and
qualified play without exposing private account activity to creator-facing reads.

## 10. Deferred scope

This work unit does not add platform-wide creator blocking, negative-feedback-based collaborative
ranking, experimentation allocation, paid-placement attribution, purchase/support conversion
attribution, a full hidden-content settings screen, or creator-facing recommendation analytics.
Those capabilities can build on the same identifiers and aggregate facts after this feedback loop is
operating reliably.
