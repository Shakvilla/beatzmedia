package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.List;

/**
 * Generic paginated response envelope. Wire shape: {@code { items, page, size, total }}.
 * Catalog ADD §4.1 / API-CONTRACT.md §1.
 */
public record PageView<T>(List<T> items, int page, int size, long total) {}
