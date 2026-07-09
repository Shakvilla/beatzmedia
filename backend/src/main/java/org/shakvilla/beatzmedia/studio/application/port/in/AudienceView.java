package org.shakvilla.beatzmedia.studio.application.port.in;

import java.util.List;

/**
 * {@code AudienceDto} — Studio ADD §6, response of {@code GET /studio/audience}
 * (LLFR-STUDIO-03.2). Traceable to {@code Frontend/src/lib/studio-analytics.ts} {@code Audience}.
 *
 * <p><strong>Real vs. honest-empty fields (Studio ADD §15).</strong> {@code rangeLabel}, {@code
 * followers}, {@code followersGained}, and {@code followersPeriod} are genuinely computed/derived
 * from {@code analytics}'s rollups (or the queried range itself). {@code monthlyListeners}/{@code
 * listenersDelta}, {@code superfans}, {@code avgSessionSec}/{@code avgSessionDelta}, {@code
 * cities}, {@code gender}, {@code ages}, and {@code superfansList} have NO backing data pipeline:
 * {@code audience_rollup.unique_listeners}/{@code completion_pct} are staged columns the rollup job
 * never actually populates (always {@code 0}), and there is no geo/demographic/per-fan dimension at
 * all — all are honestly returned empty/zero, never fabricated.
 */
public record AudienceView(
    String rangeLabel,
    long monthlyListeners,
    int listenersDelta,
    long followers,
    long followersGained,
    String followersPeriod,
    long superfans,
    long avgSessionSec,
    int avgSessionDelta,
    List<NamedValue> cities,
    Gender gender,
    List<AgeBucket> ages,
    List<SuperfanView> superfansList) {}
