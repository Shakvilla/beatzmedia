package org.shakvilla.beatzmedia.analytics.application.port.out;

import java.util.List;

import org.shakvilla.beatzmedia.analytics.domain.PlayFact;

/**
 * Output port: append-only staging store for {@link PlayFact} rows, owned exclusively by analytics.
 * Populated by the {@code PlayRecorded} event observer (artist id resolved via catalog's
 * {@code GetTrack} input port at observation time); consumed and marked processed by the audience
 * {@code RollupJob}. Analytics ADD §4.1 ({@code PlayCountSource}).
 */
public interface PlayFactRepository {

  void append(PlayFact fact);

  List<PlayFact> findUnprocessed();

  void markProcessed(List<String> factIds);
}
