package org.shakvilla.beatzmedia.admin.fakes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.admin.application.port.out.ComplianceRequestRepository;
import org.shakvilla.beatzmedia.admin.domain.ComplianceRequest;
import org.shakvilla.beatzmedia.admin.domain.ComplianceType;

/** In-memory fake for {@link ComplianceRequestRepository}. Testing-strategy §2. */
public class FakeComplianceRequestRepository implements ComplianceRequestRepository {

  private final Map<String, ComplianceRequest> requests = new LinkedHashMap<>();

  public void seed(ComplianceRequest request) {
    requests.put(request.getId(), request);
  }

  @Override
  public List<ComplianceRequest> list(ComplianceType type) {
    return requests.values().stream()
        .filter(r -> type == null || r.getType() == type)
        .toList();
  }

  @Override
  public Optional<ComplianceRequest> findById(String requestId) {
    return Optional.ofNullable(requests.get(requestId));
  }

  @Override
  public void save(ComplianceRequest request) {
    requests.put(request.getId(), request);
  }
}
