package org.shakvilla.beatzmedia.search.application.service;

// WU-CAT-3/CAT-4: wire ReleaseWentLive/ContentTakenDown observers here once those events are
// published by the catalog module. Analogous wiring for StoreItemPublished/StoreItemRemoved
// (WU-STO-1), PodcastPublished (WU-POD-1), EventPublished (WU-EVT-1), PopularityUpdated (WU-PLY-3).
//
// Each observer will be a CDI @ApplicationScoped bean with @Observes(during=AFTER_SUCCESS) on the
// event parameter, delegating to IndexEntityUseCase.index/deindex. No wiring is added here because
// the source event types do not yet exist in the codebase (constraint #3 in WU-SRCH-1 spec).
