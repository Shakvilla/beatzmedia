package org.shakvilla.beatzmedia.admin.fakes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.shakvilla.beatzmedia.admin.application.port.out.ModerationCaseRepository;
import org.shakvilla.beatzmedia.admin.domain.ModReason;
import org.shakvilla.beatzmedia.admin.domain.ModStatus;
import org.shakvilla.beatzmedia.admin.domain.ModerationCase;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/** In-memory fake for {@link ModerationCaseRepository}. Testing-strategy §2. */
public class FakeModerationCaseRepository implements ModerationCaseRepository {

  private final Map<String, ModerationCase> cases = new LinkedHashMap<>();

  @Override
  public Page<ModerationCase> list(ModStatus status, ModReason type, PageRequest page) {
    Stream<ModerationCase> stream = cases.values().stream();
    if (status != null) {
      stream = stream.filter(c -> c.getStatus() == status);
    }
    if (type != null) {
      stream = stream.filter(c -> c.getReason() == type);
    }
    List<ModerationCase> all = stream.toList();
    long total = all.size();
    int from = Math.min(page.offset(), all.size());
    int to = Math.min(from + page.size(), all.size());
    return Page.of(new ArrayList<>(all.subList(from, to)), page.page(), page.size(), total);
  }

  @Override
  public Optional<ModerationCase> findById(String caseId) {
    return Optional.ofNullable(cases.get(caseId));
  }

  @Override
  public void save(ModerationCase moderationCase) {
    cases.put(moderationCase.getId(), moderationCase);
  }

  @Override
  public Summary summary() {
    long open = cases.values().stream().filter(c -> c.getStatus() == ModStatus.OPEN).count();
    long escalated = cases.values().stream()
        .filter(c -> c.isEscalated() && c.getStatus() != ModStatus.RESOLVED)
        .count();
    return new Summary(open, escalated);
  }
}
