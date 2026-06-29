package org.shakvilla.beatzmedia.library.application.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.application.port.in.GetOwnedTrackIds;
import org.shakvilla.beatzmedia.library.application.port.out.LibraryOwnershipReader;

/** Application service for GET /v1/me/owned. LLFR-LIBRARY-01.6. */
@ApplicationScoped
public class GetOwnedTrackIdsService implements GetOwnedTrackIds {

  private final LibraryOwnershipReader ownershipReader;

  @Inject
  public GetOwnedTrackIdsService(LibraryOwnershipReader ownershipReader) {
    this.ownershipReader = ownershipReader;
  }

  @Override
  public List<String> ownedTrackIds(AccountId account) {
    return ownershipReader.ownedTrackIds(account);
  }
}
