package org.shakvilla.beatzmedia.admin.application.port.in;

import java.util.List;

import org.shakvilla.beatzmedia.admin.domain.ComplianceType;

/**
 * Input port: list compliance requests for {@code GET /v1/admin/compliance?type=} (LLFR-ADMIN-09.1),
 * optionally filtered by type. Read-only; nothing audited. Auth: super-admin (OQ-1). Admin ADD §4.1.
 */
public interface ListCompliance {

  List<ComplianceRequestView> list(ComplianceType type);
}
