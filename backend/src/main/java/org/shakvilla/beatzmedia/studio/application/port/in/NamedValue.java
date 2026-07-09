package org.shakvilla.beatzmedia.studio.application.port.in;

/**
 * {@code { name, value }} — shape shared by {@code AnalyticsDto.countries} and {@code
 * AudienceDto.cities} (Studio ADD §6). Both are always empty in this WU's implementation: the
 * {@code analytics} rollups carry no geographic dimension (see Studio ADD §15 carryover note).
 */
public record NamedValue(String name, long value) {}
