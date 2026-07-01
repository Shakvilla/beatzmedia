package org.shakvilla.beatzmedia.commerce.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.commerce.application.port.out.OwnershipReader;
import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.application.port.in.GetOwnedTrackIds;

/**
 * Implements {@link OwnershipReader} by delegating to the library module's
 * {@link GetOwnedTrackIds} input port (WU-LIB-1) — never reads library/ownership tables directly
 * (hexagonal cross-module rule). Commerce ADD §4.2.
 *
 * <p>Only {@code track} ownership is checkable today: {@code GetOwnedTrackIds} only exposes track
 * ids. Album/episode/season-pass/ticket/store ownership is introduced by {@code ownership_grant}
 * in WU-COM-2; until then those kinds are never rejected as already-owned by this adapter.
 */
@ApplicationScoped
public class LibraryOwnershipReaderAdapter implements OwnershipReader {

  private final GetOwnedTrackIds getOwnedTrackIds;

  @Inject
  public LibraryOwnershipReaderAdapter(GetOwnedTrackIds getOwnedTrackIds) {
    this.getOwnedTrackIds = getOwnedTrackIds;
  }

  @Override
  public boolean isOwned(AccountId account, CartItemKind kind, String refId) {
    if (kind != CartItemKind.track) {
      return false;
    }
    return getOwnedTrackIds.ownedTrackIds(account).contains(refId);
  }
}
