package org.shakvilla.beatzmedia.studio.application.port.in;

/**
 * {@code engagement: { completion, save, skip }} — {@code AnalyticsDto.engagement} (Studio ADD
 * §6). Always all-zero in this WU's implementation: no session/engagement dimension exists in the
 * {@code analytics} rollups (see Studio ADD §15 carryover note).
 */
public record EngagementStats(int completion, int save, int skip) {}
