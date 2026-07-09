package org.shakvilla.beatzmedia.studio.application.port.in;

import java.math.BigDecimal;

/**
 * {@code { title, streams, revenue }} — {@code AnalyticsDto.topTracks} (Studio ADD §6). Always
 * empty in this WU's implementation: {@code sales_rollup}/{@code audience_rollup} are aggregated
 * per-artist-per-bucket only, with no per-track dimension (see Studio ADD §15 carryover note).
 */
public record TopTrackStat(String title, long streams, BigDecimal revenue) {}
