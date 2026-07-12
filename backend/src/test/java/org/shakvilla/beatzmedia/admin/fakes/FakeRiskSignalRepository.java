package org.shakvilla.beatzmedia.admin.fakes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.admin.application.port.out.RiskSignalRepository;
import org.shakvilla.beatzmedia.admin.domain.RiskSignal;
import org.shakvilla.beatzmedia.admin.domain.RiskStatus;

/** In-memory fake for {@link RiskSignalRepository}. Testing-strategy §2. */
public class FakeRiskSignalRepository implements RiskSignalRepository {

  private final Map<String, RiskSignal> signals = new LinkedHashMap<>();

  public void seed(RiskSignal signal) {
    signals.put(signal.getId(), signal);
  }

  @Override
  public List<RiskSignal> list() {
    return List.copyOf(signals.values());
  }

  @Override
  public Optional<RiskSignal> findById(String signalId) {
    return Optional.ofNullable(signals.get(signalId));
  }

  @Override
  public void save(RiskSignal signal) {
    signals.put(signal.getId(), signal);
  }

  @Override
  public long countOpen() {
    return signals.values().stream().filter(s -> s.getStatus() == RiskStatus.OPEN).count();
  }
}
