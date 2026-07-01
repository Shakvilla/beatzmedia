package org.shakvilla.beatzmedia.commerce.application.port.out;

import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Output port: checks whether the caller already owns a given catalog item, used to reject
 * {@code ALREADY_OWNED} adds (LLFR-COMMERCE-01.2). Delegates to the library module's ownership
 * input port ({@code library.application.port.in.GetOwnedTrackIds}, WU-LIB-1) — never reads
 * library/ownership tables directly. Commerce ADD §4.2.
 *
 * <p>Only {@code track} ownership can be checked in this WU: album/episode/season-pass/ticket/store
 * ownership tracking is introduced by {@code ownership_grant} in WU-COM-2 (checkout settlement);
 * until then those kinds are never rejected as already-owned.
 */
public interface OwnershipReader {

  boolean isOwned(AccountId account, CartItemKind kind, String refId);
}
