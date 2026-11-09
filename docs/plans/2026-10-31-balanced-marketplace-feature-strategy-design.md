# BeatzClik Balanced Marketplace Feature Strategy

**Date:** 2026-10-31

**Status:** Validated product direction

**Inputs:** Spotify feature gap assessment and Audiomack feature gap assessment

## 1. Product direction

BeatzClik should become an ownership-first African music marketplace, not a smaller Spotify or Audiomack.

Its core promise is:

> Discover African music, build a personal collection, support creators directly, and permanently own what you buy.

Every major feature should strengthen this marketplace loop:

`Create → discover → listen → follow → buy/support → engage → earn → create again`

The strategy balances listener utility and creator growth. It combines Spotify's strongest retention mechanics with Audiomack's strongest emerging-artist, offline, promotion, and fan-engagement mechanics while preserving BeatzClik's differentiators:

- permanent music ownership;
- GHS and Mobile Money commerce;
- direct track, album, podcast, beat, merchandise, and ticket sales;
- creator tips and supporter relationships;
- collaborator splits and transparent payouts;
- Ghanaian and African editorial identity;
- events and local cultural discovery.

## 2. Product pillars

### 2.1 Reliable listening

Listening must work consistently before advanced discovery or social features are added.

The platform needs:

- reliable foreground and background playback;
- genuine offline downloads and offline playback;
- queue management and persistence;
- low-data delivery profiles;
- cross-device listening history and continuation;
- honest, recoverable states for preview, owned, unavailable, and expired media.

### 2.2 Relevant discovery

Discovery should combine editorial judgment, transparent charts, listener behavior, and local context.

The platform needs:

- distinct Top, Trending, Emerging, Genre, and Regional charts;
- Recently Played and Following feeds;
- rule-based recommendations before machine-learning ranking;
- artist and track radio;
- creator editorial pitching;
- local-scene and event discovery;
- recommendation controls and explanations.

### 2.3 Direct fan participation

Fans should be able to do more than passively stream or purchase.

The platform needs:

- release notifications;
- comments and creator replies;
- collaborative playlists;
- supporter badges and early access;
- controlled creator broadcasts;
- shareable profiles, recommendations, and campaign assets;
- event participation and ticket reminders.

### 2.4 Creator growth

Creators should be able to publish, promote, understand, engage, and earn from one operating surface.

The platform needs:

- mobile-accessible Studio workflows;
- resumable uploads;
- verification and rights-holder workflows;
- editorial and chart submissions;
- trustworthy audience and conversion analytics;
- fan segmentation;
- campaign links and promotional assets;
- merchandise, event, support, and payout tools.

### 2.5 Ownership and commerce

All monetized products should use consistent financial and entitlement rules.

The platform needs:

- settlement-authoritative ownership;
- consistent MoMo-native checkout;
- release and creator support;
- pre-purchase and early access;
- collaborator-aware settlement;
- refunds, chargebacks, revocation, and audit;
- transparent creator and collaborator statements.

## 3. Phase 1 — complete the core marketplace

Phase 1 finishes existing promises and establishes the data required for later growth features.

### 3.1 Listener playback

#### Background media session

- Integrate mobile background playback.
- Add lock-screen and notification controls.
- Support Bluetooth and headset commands.
- Handle calls, focus changes, route changes, and interruptions.
- Refresh expired signed URLs without creating duplicate plays.
- Restore the active queue and position safely after app restart.

#### Real offline downloads

- Download media into app-private storage.
- Maintain an account-scoped download manifest.
- Support queued, downloading, paused, completed, failed, expired-link, and removing states.
- Resume interrupted downloads and renew signed URLs.
- Validate file integrity before reporting success.
- Play complete local files when offline.
- Add storage usage, removal, Wi-Fi-only, and scheduled-download controls.
- Isolate protected media between accounts.

#### Queue and playback controls

- Add Play Next and Add to Queue.
- Remove and reorder individual queue items.
- Persist the queue and current position.
- Add a sleep timer.
- Make streaming quality and data-saver controls functional.

### 3.2 Listener library

- Add library search.
- Support sort by recently added, recently played, title, artist, and purchase date.
- Add first-class Owned, Downloaded, Liked, Saved Albums, Playlists, and Recently Played views.
- Show clear preview, owned, downloadable, downloaded, and unavailable states.
- Correct mobile collection mutations to match the API contract.

### 3.3 Listening history and continuation

- Record account-scoped playback history separately from royalty/financial play counting.
- Store meaningful progress, completion, skip, and repeat signals.
- Add Recently Played.
- Resume podcast episodes and long-form content.
- Continue listening between web and mobile.
- Provide retention and privacy controls for history.

### 3.4 Discovery foundations

- Rename any non-personalized “Made for you” presentation until it is genuinely personalized.
- Separate Top from Trending semantics.
- Add Emerging, Genre, Ghana, and regional charts.
- Add freshness, velocity, unique-listener, completion, save, and abuse signals.
- Add rule-based recommendations using genres, follows, likes, saves, purchases, and history.
- Add artist radio, track radio, and autoplay continuation.

### 3.5 Following and notifications

- Add a feed for new music from followed artists.
- Add a feed for new episodes from followed shows.
- Add release-day notifications.
- Add device registration and push delivery.
- Add deep links, quiet hours, deduplication, and granular preferences.
- Track delivery, open, and conversion without exposing personal fan data.

### 3.6 Creator publishing and verification

- Make Studio fully responsive and installable as a PWA.
- Support resumable, mobile-friendly upload.
- Add creator identity and rights-holder verification applications.
- Distinguish identity verified, rights verified, monetization eligible, trusted creator, and payout KYC states.
- Add status, rejection reasons, evidence, reapplication, revocation, appeal, and audit.

### 3.7 Editorial pitching

Allow a creator to submit a scheduled or pending release for editorial consideration with:

- focus track;
- genre and mood;
- hometown and target market;
- release story;
- campaign plan;
- clean/explicit state;
- collaborators and credits;
- requested surfaces;
- submission status and optional editorial feedback.

Moderation approval must remain separate from editorial selection. Paid promotion must never determine organic editorial placement.

### 3.8 Creator analytics

Replace empty or placeholder engagement fields with real event-backed metrics:

- qualified starts and 30-second plays;
- completion, skip, repeat, and listening duration;
- unique listeners and plays per listener;
- follows, saves, playlist adds, and downloads;
- purchases, tips, support, and event conversion;
- source, referrer, campaign, geography, and discovery surface;
- track-level engagement timeline;
- release funnel from impression to purchase/support.

Financial metrics must use settled ledger data and remain separate from behavioral estimates.

### 3.9 Creator promotion

- Generate release announcement cards.
- Generate available-now, support, purchase, event, and merchandise cards.
- Generate play, purchase, follower, tip, chart, and sold-out milestone cards.
- Add canonical deep links and QR codes.
- Preserve campaign source and report conversion.

### 3.10 Support and commerce

- Wire artist tipping end to end.
- Allow support for creators, tracks, albums, podcasts, and events.
- Add optional public supporter badges.
- Let fans hide their identity and amount independently.
- Add creator supporter insights and acknowledgement tools.
- Complete collaborator-aware revenue settlement and statements.
- Preserve idempotency, settlement authority, refunds, chargebacks, fraud controls, and audit.

### Phase 1 success journey

A creator uploads and pitches a release from a phone-friendly Studio. A relevant fan discovers it, listens, follows the artist, purchases or supports the release, downloads it, plays it offline, and creates accurate creator analytics and settled earnings without any simulated state.

## 4. Phase 2 — retention and community

Phase 2 turns reliable transactions into durable fan and creator relationships.

### 4.1 Personalized discovery

- Add “Because you liked…” rails.
- Add “More from this sound” and local-scene discovery.
- Generate weekly discovery and new-release playlists.
- Personalize charts by genre, region, and taste.
- Add mood, activity, language, and cultural-context discovery.
- Add Show Less, Block Artist, and Exclude from Taste Profile controls.
- Explain recommendations in clear language.

### 4.2 Richer playlists

- Reorder tracks and playlists.
- Add custom artwork and descriptions.
- Add folders, sorting, filtering, and bulk actions.
- Add collaborative playlists with invite links and roles.
- Maintain collaborator change history.
- Suggest tracks based on playlist content.
- Add imports, embeds, and artist placement notifications.

### 4.3 Comments and creator replies

- Add comments to tracks, albums, podcasts, and selected releases.
- Support creator replies and pinned comments.
- Add blocking, reporting, creator hide controls, spam detection, and rate limits.
- Add admin review, moderation history, and audit.
- Add notification preferences and safe pagination.

### 4.4 Creator broadcasts

- Allow verified creators to broadcast to followers, buyers, supporters, or event attendees.
- Add rate limits, templates, unsubscribe controls, quiet hours, and safe-link policies.
- Add moderation and audit.
- Report delivery and conversion.
- Do not introduce unrestricted one-to-one messaging in this phase.

### 4.5 Public fan participation

- Add opt-in public fan profiles.
- Let fans display selected playlists, recommendations, badges, and supported releases.
- Add a Recommend/Repost action for music-focused amplification.
- Add privacy controls before activity feeds.

### 4.6 Release campaigns

- Add public scheduled-release pages.
- Add pre-save and countdown.
- Add pre-purchase and supporter early access.
- Add Artist Pick for releases, playlists, events, merchandise, or support campaigns.
- Add release-day notification fanout and campaign conversion.

### 4.7 Events and local culture

- Recommend events using follows, location, and listening behavior.
- Add venue profiles and follows.
- Add calendar export, reminders, and ticket deep links.
- Surface relevant events on Home, artist pages, and Now Playing.
- Add verified curator/tastemaker profiles for DJs, presenters, promoters, and regional curators.

## 5. Phase 3 — strategic expansion

Phase 3 contains scale-dependent bets. Each requires a validated business case, adoption threshold, operational owner, and measurable expected outcome.

### 5.1 Advanced discovery

- Natural-language playlist generation.
- Conversational music discovery.
- Context-aware recommendations.
- A BeatzClik DJ focused on African music stories, languages, and scenes.
- Weekly and annual listening recaps.

These features require sufficient behavioral data, ranking evaluation, safety controls, and transparent feedback.

### 5.2 Media expansion

- Short creator Clips.
- Looping Now Playing visuals.
- Video podcasts with chapters, transcripts, comments, and retention analytics.
- Curated music-video partnerships.

Video requires explicit rights, moderation, bandwidth, storage, transcode, and monetization plans.

### 5.3 Multi-device listening

- Web/mobile playback continuation.
- Remote queue control.
- Casting and car integration.
- Later TV, speaker, and wearable clients.

Account-level continuity should precede a full remote-device ecosystem.

### 5.4 Business-model experiments

- Artist-opt-in free offline caching.
- Sponsored listening or downloads.
- Optional listener subscription for ad-free access, higher quality, or expanded offline use.
- Creator Pro for advanced analytics, team seats, campaign credits, and priority support.
- Clearly labeled targeted creator promotion.

A streaming royalty pool should only be introduced if advertising or subscription revenue can fund transparent and sustainable rights accounting.

## 6. Features to defer or reject

- Unrestricted fan-to-fan messaging.
- Blockchain or NFT ownership.
- Audiobooks without a specific market thesis.
- Unlimited free creator storage without cost controls.
- Paid chart manipulation.
- Public supporter spending amounts by default.
- AI recommendations without measurement, explanations, and user controls.
- Full music-video hosting without a rights and delivery strategy.
- A smart-speaker ecosystem before mobile playback is reliable.

## 7. Supporting product architecture

### 7.1 Listener intelligence

Create an engagement layer that records:

- playback starts, 30-second plays, completion, skips, repeats, and duration;
- likes, follows, saves, playlist additions, downloads, purchases, support, and event interest;
- referral source, campaign, and discovery surface;
- device and network class without unnecessary personal data.

Financial play counting must remain separate from recommendation and product analytics.

### 7.2 Discovery and ranking

A discovery capability should generate:

- Top, Trending, Emerging, Genre, Regional, and editorial collections;
- Recently Played and Following feeds;
- rule-based recommendations and radio queues;
- later model-ranked and conversational recommendations.

Each generated item should retain a reason, source, ranking version, and experiment identifier.

### 7.3 Fan relationships

Use a shared relationship model for:

- follows and notification subscriptions;
- supporter relationships and visibility preferences;
- creator broadcasts and audience segments;
- comments, reports, blocks, and moderation;
- curators, playlists, and placement relationships.

This prevents separate incompatible fan systems for tips, messages, comments, events, and releases.

### 7.4 Campaigns and promotion

A campaign capability should connect:

- creator pitches;
- pre-saves and early access;
- release notifications;
- campaign links and QR codes;
- promotional assets;
- sponsored placement;
- attribution and conversion.

Editorial and paid promotion must remain technically and visibly separate.

### 7.5 Media and playback

Playback resolves an authorized source in this order:

1. Complete local owned media.
2. Full signed stream.
3. Server-clipped preview.
4. Explicit unavailable state.

Background playback, download management, quality selection, and queue persistence should share one account-scoped playback session.

### 7.6 Commerce

All purchases and support actions continue through the existing settlement, ownership, ledger, audit, refund, and payout boundaries. No new client feature may grant access based on optimistic state.

## 8. Core data flows

### 8.1 Release-to-fan

1. A verified creator completes rights, metadata, pricing, splits, download policy, and scheduling.
2. Media processing generates preview, streaming, artwork, and optional lossless assets.
3. The creator submits for moderation and may separately submit an editorial pitch.
4. Approval schedules publication without guaranteeing promotion.
5. Followers receive notifications according to preferences.
6. Discovery ranks the release using editorial, regional, behavioral, and freshness signals.
7. Plays create separate financial and engagement events.
8. Purchase/support enters payment processing.
9. Ownership and earnings appear only after settlement.
10. Analytics update with privacy-safe engagement and settled financial data.

### 8.2 Offline download

1. The client verifies account ownership and download entitlement.
2. It requests a signed download URL.
3. It streams into a temporary app-private file.
4. An expired link is renewed without marking failure prematurely.
5. The complete file is validated.
6. The manifest is atomically updated.
7. Only then does the item become playable offline.
8. Sign-out makes the media inaccessible to other accounts.

The manifest retains owner, track, local location, checksum, format, completion, and license state. It never persists the signed URL as the durable media location.

## 9. Safeguards and error behavior

### 9.1 Recommendation safeguards

- Minimum-data thresholds.
- Recommendation reasons.
- Negative-feedback controls.
- Blocked-artist and explicit-content enforcement.
- Separation of sponsored and organic ranking.
- Artificial-stream and coordinated-manipulation detection.
- Versioned, measurable, reversible ranking changes.

### 9.2 Community safeguards

- Rate limits, reporting, blocking, moderation, and audit.
- Notification opt-out and quiet hours.
- Privacy-preserving supporter segmentation.
- Safe-link policy for creator broadcasts.
- Explicit policy decisions for minors, public profiles, direct messaging, and spending visibility.

### 9.3 Failure behavior

Uploads, downloads, payments, media processing, notifications, and campaigns must expose distinct states:

- pending;
- processing;
- retryable;
- failed;
- rejected;
- expired;
- unavailable;
- completed.

The product must never report completion before the authoritative backend transition.

## 10. Testing and quality gates

### 10.1 Playback and offline matrix

Test across:

- iOS and Android lifecycle changes;
- background, screen lock, termination, calls, and interruptions;
- Wi-Fi/mobile transitions and no connectivity;
- expired streams and interrupted downloads;
- Bluetooth, headset, lock-screen, and notification controls;
- account switching, sign-out, reinstall, and low storage;
- owned, free, preview, revoked, and unavailable content.

### 10.2 Financial journeys

Test:

- idempotent purchase and support;
- pending settlement and timeout;
- failure and retry;
- refund and chargeback;
- ownership grant and revocation;
- collaborator allocation;
- balanced ledger entries;
- payout eligibility and completion.

### 10.3 Feature acceptance gates

Every feature should include:

- domain and application tests;
- API contract tests that fail when zero tests execute;
- database and object-storage integration tests;
- web and mobile component tests;
- an automated end-to-end journey;
- accessibility validation;
- analytics-event validation;
- abuse and moderation cases where relevant;
- load tests for fanout, charts, comments, and media delivery where relevant.

## 11. Product measurement

### 11.1 North-star metric

> **Monthly active purchasing or supporting fans who complete at least one qualified listening session.**

This connects listening activity to the marketplace and avoids optimizing empty stream volume.

### 11.2 Listener metrics

- Weekly active listeners.
- Qualified listening hours.
- Day-7 and Day-30 retention.
- Playback and offline success rates.
- Follow-to-return conversion.
- Recommendation-to-play conversion.
- Preview-to-purchase conversion.
- Repeat purchase/support rate.

### 11.3 Creator metrics

- Verified active creators.
- Time from upload to publication.
- Percentage receiving meaningful discovery.
- Listener-to-follower conversion.
- Follower-to-buyer/supporter conversion.
- Median creator earnings.
- Payout completion time.
- Campaign and pitch performance.

### 11.4 Guardrails

- Refund and chargeback rate.
- Fraudulent plays and purchases.
- Moderation reports and response time.
- Notification unsubscribe rate.
- Playback and download failure rates.
- Creator revenue concentration.
- Supporter privacy complaints.

## 12. Product sequencing rule

Build complete vertical journeys rather than isolated feature inventories.

The first delivery program should prove this journey:

> A creator uploads and pitches a release; a relevant fan discovers it, listens, follows, buys or supports it, downloads it, and generates accurate creator analytics and settled earnings.

Features that do not improve the reliability, conversion, retention, or economics of this journey should not displace Phase 1 work.
