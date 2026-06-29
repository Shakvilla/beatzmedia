package org.shakvilla.beatzmedia.catalog.application.port.in;

/** Read-model for a browse category. LLFR-CATALOG-01.3, WU-CAT-2. */
public record BrowseCategoryView(String id, String title, String colorClass) {}
