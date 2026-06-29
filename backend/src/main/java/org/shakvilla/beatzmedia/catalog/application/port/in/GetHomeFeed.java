package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.Optional;

/** Input port: retrieve the home feed. LLFR-CATALOG-01.1, WU-CAT-2. */
public interface GetHomeFeed {
  HomeFeedView get(Optional<String> callerId);
}
