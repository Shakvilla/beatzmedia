package org.shakvilla.beatzmedia.library.adapter.out.persistence;

import java.util.List;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.application.port.out.LibraryOwnershipReader;

/**
 * Superseded stub implementation of {@link LibraryOwnershipReader}. Retained as a deprioritised CDI
 * {@code @Alternative} (NOT the active bean) now that WU-COM-2 ships
 * {@code commerce.adapter.out.integration.CommerceLibraryOwnershipReaderAdapter}, which reads real
 * {@code ownership_grant} rows. Library ADD §10 / INV-LIB-5.
 *
 * <p>ADR (WU-LIB-1): commerce did not exist yet when library shipped; this stub satisfied the port so
 * the library module compiled and tested independently. WU-COM-2 supplies the real commerce-backed
 * adapter, so this bean is now inactive (an unselected {@code @Alternative}) — kept only so the
 * library module still resolves the port if the commerce adapter is ever excluded from a build.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class StubLibraryOwnershipReaderAdapter implements LibraryOwnershipReader {

  @Override
  public List<String> ownedTrackIds(AccountId account) {
    return List.of();
  }
}
