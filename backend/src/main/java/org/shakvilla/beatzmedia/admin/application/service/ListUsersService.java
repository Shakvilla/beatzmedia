package org.shakvilla.beatzmedia.admin.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.ListUsers;
import org.shakvilla.beatzmedia.admin.application.port.in.PagedUsersView;
import org.shakvilla.beatzmedia.admin.application.port.in.UserQuery;
import org.shakvilla.beatzmedia.admin.application.port.out.IdentityReader;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Application service for LLFR-ADMIN-02.1 (paged/filtered user list + counts). Auth: any admin
 * role (enforced entirely by the inbound {@code @RolesAllowed}; no application-layer narrowing —
 * same convention as {@code GetOverviewService}/{@code AdminSupportResource}). Reads are never
 * audited (admin ADD §9).
 */
@ApplicationScoped
public class ListUsersService implements ListUsers {

  private final IdentityReader identityReader;

  @Inject
  public ListUsersService(IdentityReader identityReader) {
    this.identityReader = identityReader;
  }

  @Override
  @Transactional
  public PagedUsersView list(UserQuery query, PageRequest page) {
    Page<IdentityReader.AccountRow> result =
        identityReader.listUsers(query.q(), query.filter(), page);

    return new PagedUsersView(
        result.items().stream().map(AdminUserMapper::toView).toList(),
        result.page(),
        result.size(),
        result.total(),
        identityReader.countUsers());
  }
}
