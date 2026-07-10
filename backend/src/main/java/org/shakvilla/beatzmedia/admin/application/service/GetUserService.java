package org.shakvilla.beatzmedia.admin.application.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.ActionLogEntryView;
import org.shakvilla.beatzmedia.admin.application.port.in.GetUser;
import org.shakvilla.beatzmedia.admin.application.port.in.UserDetailView;
import org.shakvilla.beatzmedia.admin.application.port.out.IdentityReader;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditReader;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditFilter;
import org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Application service for LLFR-ADMIN-02.1 (user detail). Auth: any admin role (read; no
 * application-layer narrowing). Composes:
 *
 * <ul>
 *   <li>{@code summary} — real account row via {@link IdentityReader} (Category A)
 *   <li>{@code actionLog} — real, most-recent-20 {@code audit_entry} rows targeting this account
 *       id via {@code audit}'s {@link AuditReader} output port, called in-process (a genuine
 *       cross-module output-port read, not a raw JPA read — see admin ADD §13, WU-ADM-2 as-built)
 *   <li>{@code activity}/{@code orders}/{@code devices} — honest empty arrays (Category B, no
 *       backing subsystem — see {@link UserDetailView}'s javadoc)
 * </ul>
 */
@ApplicationScoped
public class GetUserService implements GetUser {

  /** Most-recent-N action-log entries shown on the user detail page. */
  private static final int ACTION_LOG_SIZE = 20;

  private final IdentityReader identityReader;
  private final AuditReader auditReader;

  @Inject
  public GetUserService(IdentityReader identityReader, AuditReader auditReader) {
    this.identityReader = identityReader;
    this.auditReader = auditReader;
  }

  @Override
  @Transactional
  public UserDetailView get(String targetId) {
    IdentityReader.AccountRow row =
        identityReader.findUser(targetId).orElseThrow(() -> new AccountNotFoundException(targetId));

    Page<AuditEntry> auditPage = auditReader.query(
        AuditFilter.byTargetId(targetId), new PageRequest(1, ACTION_LOG_SIZE));
    List<ActionLogEntryView> actionLog =
        auditPage.items().stream().map(GetUserService::toActionLogEntry).toList();

    return new UserDetailView(
        AdminUserMapper.toView(row), List.of(), List.of(), List.of(), actionLog);
  }

  private static ActionLogEntryView toActionLogEntry(AuditEntry entry) {
    String by = entry.getActorName() != null ? entry.getActorName() : entry.getActor();
    return new ActionLogEntryView(entry.getId(), entry.getAction(), by, entry.getOccurredAt());
  }
}
