package org.shakvilla.beatzmedia.library.adapter.out.persistence;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.application.port.out.LibraryOwnershipReader;

/**
 * Stub implementation of {@link LibraryOwnershipReader}. Returns an empty list until the commerce
 * module (WU-COM-*) ships its real ownership-grant persistence. Library ADD §10 / INV-LIB-5.
 *
 * <p>ADR (WU-LIB-1): commerce does not exist yet; this stub satisfies the port so the library
 * module compiles and tests independently. Once WU-COM-1 ships, this stub will be replaced by the
 * commerce-backed adapter.
 */
@ApplicationScoped
public class StubLibraryOwnershipReaderAdapter implements LibraryOwnershipReader {

  @Override
  public List<String> ownedTrackIds(AccountId account) {
    return List.of();
  }
}
