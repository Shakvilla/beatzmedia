package org.shakvilla.beatzmedia.commerce.adapter.out.integration;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.commerce.application.port.out.OwnershipRepository;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.application.port.out.LibraryOwnershipReader;

/**
 * Commerce-backed implementation of the library {@code LibraryOwnershipReader} output port (library
 * ADD §4.2 / INV-LIB-5), reading real {@code ownership_grant} rows created on settlement (WU-COM-2).
 * The sole bean for this port — the WU-LIB-1-era stub (which always reported nothing owned) was
 * removed in WU-PLY-1 after it was found to still be globally active as a CDI {@code @Alternative},
 * silently defeating real ownership checks app-wide (library's {@code /me/owned}, commerce's
 * ALREADY_OWNED cart guard, and playback's INV-3 gate all read through this port).
 *
 * <p><strong>Hexagonal boundary preserved.</strong> Ownership is owned by commerce (it is created only
 * on settlement, INV-1). Library declares the read as an output <em>port</em> and this bean supplies
 * it in-process, reading ONLY commerce's own {@code ownership_grant} rows through commerce's
 * {@link OwnershipRepository} — no cross-module table write, no shared persistence. Library never sees
 * a commerce entity; it receives plain track-id strings.
 */
@ApplicationScoped
public class CommerceLibraryOwnershipReaderAdapter implements LibraryOwnershipReader {

  private final OwnershipRepository ownershipRepository;

  @Inject
  public CommerceLibraryOwnershipReaderAdapter(OwnershipRepository ownershipRepository) {
    this.ownershipRepository = ownershipRepository;
  }

  @Override
  @Transactional
  public List<String> ownedTrackIds(AccountId account) {
    return ownershipRepository.activeTrackIds(account);
  }
}
