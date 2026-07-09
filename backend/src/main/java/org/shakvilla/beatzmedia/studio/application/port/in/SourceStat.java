package org.shakvilla.beatzmedia.studio.application.port.in;

/**
 * {@code { name, pct }} — {@code AnalyticsDto.sources} (Studio ADD §6). Always empty in this WU's
 * implementation: no traffic-source dimension exists in the {@code analytics} rollups (see Studio
 * ADD §15 carryover note).
 */
public record SourceStat(String name, int pct) {}
