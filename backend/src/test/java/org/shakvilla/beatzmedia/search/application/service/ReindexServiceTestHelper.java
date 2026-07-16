package org.shakvilla.beatzmedia.search.application.service;

import java.util.List;

import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.search.application.port.in.ReindexUseCase;
import org.shakvilla.beatzmedia.search.application.port.out.IndexSource;
import org.shakvilla.beatzmedia.search.application.port.out.SearchIndex;

/** Exposes the package-private {@link ReindexService} to tests without CDI. */
public final class ReindexServiceTestHelper {

  private ReindexServiceTestHelper() {}

  public static ReindexUseCase create(SearchIndex searchIndex, Clock clock, List<IndexSource> sources) {
    return new ReindexService(searchIndex, clock, sources);
  }
}
