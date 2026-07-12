package org.shakvilla.beatzmedia.admin.application.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.ComplianceRequestView;
import org.shakvilla.beatzmedia.admin.application.port.in.ListCompliance;
import org.shakvilla.beatzmedia.admin.application.port.out.ComplianceRequestRepository;
import org.shakvilla.beatzmedia.admin.domain.ComplianceType;

/**
 * Read service for {@link ListCompliance} (LLFR-ADMIN-09.1). Lists compliance requests from this
 * module's own {@code compliance_request} table, optionally filtered by type. Read-only; nothing
 * audited. Super-admin scope is enforced at the inbound resource.
 */
@ApplicationScoped
public class ListComplianceService implements ListCompliance {

  private final ComplianceRequestRepository requests;

  @Inject
  public ListComplianceService(ComplianceRequestRepository requests) {
    this.requests = requests;
  }

  @Override
  @Transactional
  public List<ComplianceRequestView> list(ComplianceType type) {
    return requests.list(type).stream().map(ComplianceRequestView::of).toList();
  }
}
