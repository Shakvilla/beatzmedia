package org.shakvilla.beatzmedia.studio.application.port.in;

import java.util.List;

/**
 * {@code MetricSeries { total, delta, current[], previous[] }} — Studio ADD §6, mirroring {@code
 * Frontend/src/lib/studio-analytics.ts} {@code MetricSeries}. A faithful 1:1 passthrough of the
 * {@code analytics} module's rollup-derived series (streams/sales/followers/tips counted per
 * bucket); see Studio ADD §15 for the note on why the {@code sales}/{@code tips} series carry raw
 * minor units (pesewas) here rather than the decimal-cedis convention used by {@code
 * AnalyticsDto.revenue}.
 */
public record MetricSeriesView(long total, int delta, List<Long> current, List<Long> previous) {}
