package org.shakvilla.beatzmedia.studio.application.port.in;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;

import org.shakvilla.beatzmedia.studio.domain.ArtistId;

/**
 * Input port: {@code POST /studio/podcasts/episodes} — LLFR-STUDIO-02.3. Multipart: an {@code
 * audio} file part + a {@code CreateEpisodeDto} JSON part. Requires an {@code Idempotency-Key}
 * (mandatory — 400/422 if missing, mirroring {@code commerce.CheckoutResource}); a replay of the
 * same key returns the same created episode with no second media upload. Studio ADD §4.1 / §9.
 *
 * <p>{@code cover} is a plain URL string carried inside the {@code CreateEpisodeDto} JSON part
 * (API-CONTRACT.md §11 — {@code cover?} sits alongside {@code title}/{@code description} in the
 * JSON object, not as a second multipart file part), mirroring how {@code studio_profile.avatar_url}
 * is already a client-supplied URL rather than a binary upload. Only {@code audio} is a real
 * multipart file part that studio forwards to the media pipeline.
 */
public interface CreateEpisode {

  EpisodeView create(ArtistId artist, String idempotencyKey, CreateEpisodeCommand cmd, AudioUpload audio);

  /**
   * {@code CreateEpisodeDto} — Studio ADD §6. Exactly one of {@code showId} / ({@code newShowTitle}
   * + {@code newShowCategory}) must be supplied. {@code visibility} is {@code "public"} or {@code
   * "scheduled"}; {@code date} is required and must be strictly in the future when {@code
   * "scheduled"}. {@code priceCedis} is required (&gt; 0) when {@code premium}.
   */
  record CreateEpisodeCommand(
      String showId,
      String newShowTitle,
      String newShowCategory,
      String title,
      String description,
      String coverUrl,
      String visibility,
      Instant date,
      boolean premium,
      BigDecimal priceCedis,
      boolean earlyAccess) {}

  /** Multipart audio upload descriptor — mirrors {@code catalog.UploadReleaseTrack.AudioUpload}. */
  record AudioUpload(
      String filename, String contentType, long sizeBytes, InputStream body, String contentHash) {}
}
