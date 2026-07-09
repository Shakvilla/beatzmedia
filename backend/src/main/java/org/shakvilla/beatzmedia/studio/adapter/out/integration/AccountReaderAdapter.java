package org.shakvilla.beatzmedia.studio.adapter.out.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.identity.application.port.in.AccountView;
import org.shakvilla.beatzmedia.identity.application.port.in.GetCurrentAccount;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.studio.application.port.out.AccountReader;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;

/**
 * Implements studio's {@link AccountReader} output port by calling identity's {@link
 * GetCurrentAccount} INPUT port in-process — studio never reads identity's {@code account} table
 * directly. Mirrors {@code OwnershipReaderAdapter} (WU-STU-2), the actual as-built precedent for
 * "wrap another module's in-process input port behind a studio-owned output port + adapter" (see
 * studio.md §15's note on why {@code OwnershipReaderAdapter}, not a fictional {@code MediaService},
 * is the real precedent to follow). Studio ADD §5.2 (WU-STU-4 addition).
 */
@ApplicationScoped
public class AccountReaderAdapter implements AccountReader {

  private final GetCurrentAccount getCurrentAccount;

  @Inject
  public AccountReaderAdapter(GetCurrentAccount getCurrentAccount) {
    this.getCurrentAccount = getCurrentAccount;
  }

  @Override
  public String emailOf(ArtistId artist) {
    AccountView view = getCurrentAccount.current(new AccountId(artist.value()));
    return view == null || view.email() == null ? "" : view.email();
  }
}
