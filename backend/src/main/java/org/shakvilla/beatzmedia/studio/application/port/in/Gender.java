package org.shakvilla.beatzmedia.studio.application.port.in;

/**
 * {@code gender: { male, female, other }} — {@code AudienceDto.gender} (Studio ADD §6). Always
 * all-zero in this WU's implementation: no demographic dimension exists in the {@code analytics}
 * rollups (see Studio ADD §15 carryover note).
 */
public record Gender(int male, int female, int other) {}
