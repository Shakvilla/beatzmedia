package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.Map;

/** Read-model for the top search result. LLFR-CATALOG-01.2, WU-CAT-2. */
public record TopResultView(
    String entityType,
    String entityId,
    String title,
    String subtitle,
    Map<String, Object> payload) {}
