package org.shakvilla.beatzmedia.admin.application.service;

import java.time.Instant;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.ComplianceRequestView;
import org.shakvilla.beatzmedia.admin.application.port.in.ListCompliance;
import org.shakvilla.beatzmedia.admin.application.port.out.ComplianceRequestRepository;
import org.shakvilla.beatzmedia.admin.domain.ComplianceType;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;

/**
 * Read service for {@link ListCompliance} (LLFR-ADMIN-09.1). Lists compliance requests from this
 * module's own {@code compliance_request} table, optionally filtered by type. Read-only; nothing
 * audited. Super-admin scope is enforced at the inbound resource.
 */
@ApplicationScoped
public class ListComplianceService implements ListCompliance {

  private final ComplianceRequestRepository requests;
  private final Clock clock;

  @Inject
  public ListComplianceService(ComplianceRequestRepository requests, Clock clock) {
    this.requests = requests;
    this.clock = clock;
  }

  @Override
  @Transactional
  public List<ComplianceRequestView> list(ComplianceType type) {
    Instant now = clock.now();
    return requests.list(type).stream().map(r -> ComplianceRequestView.of(r, now)).toList();
  }
}
