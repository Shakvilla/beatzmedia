package org.shakvilla.beatzmedia.search.application.service;

// Index-freshness wiring, tracked here because the observers deliberately live in the OWNING module
// rather than in search — catalog owns its data and its document mapping, and search never reads
// catalog (same direction as TrackIndexSource and store's SearchIndexPg).
//
// DONE:
//   ReleaseWentLive / ContentTakenDown -> catalog.adapter.out.search.ReleaseSearchProjectionObserver
//     Covers admin approval, the scheduled go-live job, reinstate and takedown in one place, since
//     PublishReleaseService fires those two events on all four paths.
//
// STILL UNWIRED — these are refreshed only by the periodic reindex, so a change is not searchable
// (or stays searchable after removal) until the next backfill runs:
//   StoreItemPublished / StoreItemRemoved (WU-STO-1)
//   PodcastPublished                      (WU-POD-1)
//   EventPublished                        (WU-EVT-1)
//   PopularityUpdated                     (WU-PLY-3)
//
// Each follows the same shape: an @ApplicationScoped bean in the owning module with
// @Observes(during = AFTER_SUCCESS) plus @Transactional(REQUIRES_NEW), delegating to
// IndexEntityUseCase.index/deindex.
