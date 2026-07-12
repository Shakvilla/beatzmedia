package org.shakvilla.beatzmedia.admin.application.port.out;

import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.admin.domain.ComplianceRequest;
import org.shakvilla.beatzmedia.admin.domain.ComplianceType;

/**
 * Output port for {@link ComplianceRequest} persistence (owns {@code compliance_request}; this
 * module's table only). Implemented by a JPA adapter in {@code adapter.out.persistence}. Admin ADD
 * §4.2 / §7 (LLFR-ADMIN-09.1).
 */
public interface ComplianceRequestRepository {

  /** Compliance requests, optionally filtered by {@code type}, ordered newest-first. */
  List<ComplianceRequest> list(ComplianceType type);

  /** Loads a single request, or empty if not found. */
  Optional<ComplianceRequest> findById(String requestId);

  /** Upsert: persists the request's current state. */
  void save(ComplianceRequest request);
}
