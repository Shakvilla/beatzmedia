package org.shakvilla.beatzmedia.identity.application.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.identity.application.port.in.AdminMemberView;
import org.shakvilla.beatzmedia.identity.application.port.in.ListAdminTeam;
import org.shakvilla.beatzmedia.identity.application.port.out.AccountRepository;

/**
 * Application service for LLFR-IDENTITY-03.1 (list admin team). Auth: any admin (read). Identity
 * ADD §4.1 / §10.
 */
@ApplicationScoped
public class ListAdminTeamService implements ListAdminTeam {

  private final AccountRepository accountRepository;

  @Inject
  public ListAdminTeamService(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  @Override
  @Transactional
  public List<AdminMemberView> list() {
    return accountRepository.findAllAdminMembers().stream()
        .map(AdminTeamMapper::toView)
        .toList();
  }
}
