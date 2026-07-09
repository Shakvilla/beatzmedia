package org.shakvilla.beatzmedia.studio.application.port.in;

/**
 * {@code { label, value }} — shape shared by {@code AnalyticsDto.ages} and {@code
 * AudienceDto.ages} (Studio ADD §6). Always empty in this WU's implementation: the {@code
 * analytics} rollups carry no demographic dimension (see Studio ADD §15 carryover note).
 */
public record AgeBucket(String label, long value) {}
