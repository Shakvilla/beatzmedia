package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.List;

/**
 * Read-model for a track credit. Matches the {@code TrackCredit} TypeScript type. Catalog ADD §6.
 */
public record TrackCreditView(String role, List<String> names) {}
