package org.shakvilla.beatzmedia.podcasts.adapter.in.rest;

import org.shakvilla.beatzmedia.podcasts.domain.TipResult;

/**
 * Response body for {@code POST /v1/podcasts/:id/tip} (HTTP 202). Podcasts ADD §6 / API-CONTRACT §8.
 *
 * <p>{@code status} mirrors the payment intent's coarse state (e.g. {@code PENDING} / {@code
 * INITIATED} / {@code SETTLED}); {@code tipId} is the payments intent id the client can poll. No
 * value has moved yet (INV-1) — the 90/10 split (INV-4) posts on settlement.
 *
 * @param tipId the payments-issued tip/intent id
 * @param status the coarse tip status string
 */
public record TipResponse(String tipId, String status) {

  static TipResponse from(TipResult result) {
    return new TipResponse(result.tipId(), result.status());
  }
}
