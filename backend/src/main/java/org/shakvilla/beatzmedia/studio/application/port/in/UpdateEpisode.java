package org.shakvilla.beatzmedia.studio.application.port.in;

import java.math.BigDecimal;
import java.time.Instant;

import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.EpisodeId;

/**
 * Input port: {@code PATCH /studio/podcasts/episodes/:id} — LLFR-STUDIO-02.4. PATCH semantics:
 * {@code null} = no change. Fires {@code EpisodePublished} when the update transitions the episode
 * {@code draft -> published}. Studio ADD §4.1.
 */
public interface UpdateEpisode {

  EpisodeView update(ArtistId artist, EpisodeId id, UpdateEpisodeCommand cmd);

  /**
   * {@code UpdateEpisodeDto} — Studio ADD §6. All fields optional; {@code null} = no change.
   * {@code visibility}, when present, is one of {@code public|scheduled|draft} ({@code draft}
   * unschedules a {@code scheduled} episode).
   */
  record UpdateEpisodeCommand(
      String title,
      String description,
      Boolean premium,
      BigDecimal priceCedis,
      String visibility,
      Instant date,
      Boolean earlyAccess) {}
}
