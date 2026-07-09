package org.shakvilla.beatzmedia.studio.application.port.in;

/**
 * {@code { handle, initial, tracks, tipped }} — {@code AudienceDto.superfansList} (Studio ADD §6).
 * Always empty in this WU's implementation: computing a per-fan spend/engagement leaderboard would
 * require a new per-fan aggregation dimension that doesn't exist in the {@code analytics} rollups
 * (see Studio ADD §15 carryover note).
 */
public record SuperfanView(String handle, String initial, int tracks, int tipped) {}
