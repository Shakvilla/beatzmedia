package org.shakvilla.beatzmedia.studio.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.media.application.port.in.UploadCommand;
import org.shakvilla.beatzmedia.media.application.port.in.UploadOriginalUseCase;
import org.shakvilla.beatzmedia.media.domain.MediaHandle;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.OwnerRef;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;
import org.shakvilla.beatzmedia.studio.application.port.in.CreateEpisode;
import org.shakvilla.beatzmedia.studio.application.port.in.EpisodeView;
import org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.Episode;
import org.shakvilla.beatzmedia.studio.domain.EpisodeId;
import org.shakvilla.beatzmedia.studio.domain.EpisodePublished;
import org.shakvilla.beatzmedia.studio.domain.IdempotencyConflictException;
import org.shakvilla.beatzmedia.studio.domain.InvalidPriceException;
import org.shakvilla.beatzmedia.studio.domain.MediaInvalidException;
import org.shakvilla.beatzmedia.studio.domain.PodcastShow;
import org.shakvilla.beatzmedia.studio.domain.ScheduleDateRequiredException;
import org.shakvilla.beatzmedia.studio.domain.ShowId;
import org.shakvilla.beatzmedia.studio.domain.ShowNotFoundException;

/**
 * Application service for {@link CreateEpisode} — LLFR-STUDIO-02.3. Studio ADD §4.1 / §5.2 / §8 /
 * §9:
 *
 * <ul>
 *   <li><strong>Idempotency.</strong> A replay of the same {@code (artist, idempotencyKey)} returns
 *       the previously-created episode verbatim — no second call to {@link UploadOriginalUseCase}.
 *       Replaying the key with a materially different request is rejected (409 {@code
 *       IDEMPOTENCY_KEY_CONFLICT}), mirroring {@code commerce.CheckoutService}.
 *   <li><strong>Media upload happens before the DB commit.</strong> {@link UploadOriginalUseCase
 *       #uploadOriginal} is called inside this {@code @Transactional} method BEFORE {@code
 *       repo.saveEpisode(..)}; if the upload throws, the whole transaction rolls back and no
 *       episode row is ever persisted (ADD §5.2) — mirrors {@code
 *       catalog.UploadReleaseTrackService}'s transaction boundary exactly.
 *   <li><strong>Premium ⇒ price &gt; 0</strong> (422 {@code INVALID_PRICE}); <strong>scheduled ⇒
 *       date required &amp; future</strong> (422 {@code SCHEDULE_DATE_REQUIRED}); unknown/missing
 *       show (404 {@code SHOW_NOT_FOUND}).
 *   <li>Fires {@code EpisodePublished} exactly once when {@code visibility == public} (publish-now).
 * </ul>
 */
@ApplicationScoped
public class CreateEpisodeService implements CreateEpisode {

  /** Same allow-list as {@code catalog.UploadReleaseTrackService} (WAV/FLAC only). */
  private static final Set<String> ALLOWED_AUDIO_TYPES =
      Set.of("audio/wav", "audio/x-wav", "audio/flac", "audio/x-flac");

  private final StudioRepository repo;
  private final UploadOriginalUseCase uploadOriginalUseCase;
  private final IdGenerator ids;
  private final Clock clock;
  private final AuditWriter auditWriter;
  private final Event<EpisodePublished> episodePublishedEvent;

  @Inject
  public CreateEpisodeService(
      StudioRepository repo,
      UploadOriginalUseCase uploadOriginalUseCase,
      IdGenerator ids,
      Clock clock,
      AuditWriter auditWriter,
      Event<EpisodePublished> episodePublishedEvent) {
    this.repo = repo;
    this.uploadOriginalUseCase = uploadOriginalUseCase;
    this.ids = ids;
    this.clock = clock;
    this.auditWriter = auditWriter;
    this.episodePublishedEvent = episodePublishedEvent;
  }

  @Override
  @Transactional
  public EpisodeView create(
      ArtistId artist, String idempotencyKey, CreateEpisodeCommand cmd, AudioUpload audio) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new ValidationException("Idempotency-Key is required", "Idempotency-Key");
    }
    if (cmd == null || cmd.title() == null || cmd.title().isBlank()) {
      throw new ValidationException("title is required", "title");
    }

    String hash = requestHash(cmd);

    // 1. Idempotency short-circuit — same key returns the same episode, no second upload.
    Optional<Episode> existing = repo.findEpisodeByIdempotencyKey(artist, idempotencyKey);
    if (existing.isPresent()) {
      if (!hash.equals(existing.get().requestHash())) {
        throw new IdempotencyConflictException(
            "Idempotency-Key already used with a different create-episode request");
      }
      PodcastShow show = repo.findShow(artist, existing.get().showId()).orElse(null);
      return EpisodeMapper.toView(existing.get(), show != null ? show.title() : null);
    }

    // 2. Resolve or create the show.
    PodcastShow show = resolveShow(artist, cmd);

    // 3. Premium ⇒ price > 0 (422 INVALID_PRICE).
    long priceMinor = 0L;
    if (cmd.premium()) {
      if (cmd.priceCedis() == null || cmd.priceCedis().signum() <= 0) {
        throw new InvalidPriceException("Premium episodes require a price greater than 0");
      }
      priceMinor = Money.ofCedis(cmd.priceCedis(), Currency.GHS).minor();
    } else if (cmd.priceCedis() != null && cmd.priceCedis().signum() > 0) {
      priceMinor = Money.ofCedis(cmd.priceCedis(), Currency.GHS).minor();
    }

    // 4. Visibility ⇒ scheduled requires a strictly-future date (422 SCHEDULE_DATE_REQUIRED).
    boolean scheduled = isScheduledVisibility(cmd.visibility());
    if (scheduled && (cmd.date() == null || !cmd.date().isAfter(clock.now()))) {
      throw new ScheduleDateRequiredException(
          "visibility=scheduled requires a 'date' strictly in the future");
    }

    // 5. Coarse pre-validation of the audio part (422 MEDIA_INVALID) — BEFORE the media call.
    if (audio == null || audio.body() == null) {
      throw new MediaInvalidException("An 'audio' file part is required");
    }
    String contentType = audio.contentType() != null ? audio.contentType().toLowerCase() : "";
    if (!ALLOWED_AUDIO_TYPES.contains(contentType)) {
      throw new MediaInvalidException("Only WAV/FLAC accepted, got: " + audio.contentType());
    }

    // 6. Media upload happens BEFORE the episode row is ever written (ADD §5.2): a failed upload
    // (magic-byte / virus-scan / size) throws here, rolling back this whole transaction — no
    // orphaned episode row.
    String episodeId = ids.newId();
    OwnerRef ownerRef = new OwnerRef("studio", episodeId);
    UploadCommand uploadCommand = new UploadCommand(
        ownerRef, MediaKind.AUDIO, audio.filename(), audio.contentType(), audio.sizeBytes(),
        audio.body(), audio.contentHash());
    MediaHandle handle = uploadOriginalUseCase.uploadOriginal(uploadCommand);

    // 7. Build + transition the episode.
    Instant now = clock.now();
    Episode episode = Episode.createDraft(
        new EpisodeId(episodeId),
        show.id(),
        artist,
        cmd.title(),
        cmd.description(),
        handle.assetId().value(),
        cmd.coverUrl(),
        handle.durationSec(),
        cmd.premium(),
        priceMinor,
        Currency.GHS,
        cmd.earlyAccess(),
        now,
        idempotencyKey,
        hash);

    boolean publishNow;
    if (scheduled) {
      episode.scheduleAt(cmd.date());
      publishNow = false;
    } else {
      episode.publishNow(now);
      publishNow = true;
    }

    Episode saved = repo.saveEpisode(episode);

    // INV-10: audit privileged mutation atomically in the same transaction.
    auditWriter.append(new AuditEntry(
        ids.newId(), artist.value(), "CREATE_EPISODE", "Episode", episodeId, AuditType.CATALOG,
        null, now));

    if (publishNow) {
      episodePublishedEvent.fire(
          new EpisodePublished(saved.id().value(), saved.showId().value(), artist.value(), now));
    }

    return EpisodeMapper.toView(saved, show.title());
  }

  private PodcastShow resolveShow(ArtistId artist, CreateEpisodeCommand cmd) {
    if (cmd.showId() != null && !cmd.showId().isBlank()) {
      return repo.findShow(artist, new ShowId(cmd.showId()))
          .orElseThrow(() -> new ShowNotFoundException(cmd.showId()));
    }
    if (cmd.newShowTitle() == null || cmd.newShowTitle().isBlank()) {
      throw new ValidationException(
          "Either 'showId' or 'newShow.title' is required", "showId");
    }
    String category = cmd.newShowCategory() == null || cmd.newShowCategory().isBlank()
        ? "General"
        : cmd.newShowCategory();
    PodcastShow show =
        PodcastShow.create(new ShowId(ids.newId()), artist, cmd.newShowTitle(), category, clock.now());
    return repo.saveShow(show);
  }

  private static boolean isScheduledVisibility(String visibility) {
    if (visibility == null) {
      throw new ValidationException("visibility is required", "visibility");
    }
    return switch (visibility) {
      case "public" -> false;
      case "scheduled" -> true;
      default -> throw new ValidationException(
          "visibility must be 'public' or 'scheduled'", "visibility");
    };
  }

  /** SHA-256 over the idempotency-relevant metadata fields (NOT the audio bytes — the audio is the
   * one thing idempotency exists specifically to avoid re-uploading). Mirrors {@code
   * commerce.CheckoutService#requestHash}. */
  private static String requestHash(CreateEpisodeCommand cmd) {
    String canonical = String.join(
        "#",
        String.valueOf(cmd.showId()),
        String.valueOf(cmd.newShowTitle()),
        String.valueOf(cmd.newShowCategory()),
        String.valueOf(cmd.title()),
        String.valueOf(cmd.description()),
        String.valueOf(cmd.coverUrl()),
        String.valueOf(cmd.visibility()),
        String.valueOf(cmd.date()),
        String.valueOf(cmd.premium()),
        cmd.priceCedis() == null ? "" : cmd.priceCedis().stripTrailingZeros().toPlainString(),
        String.valueOf(cmd.earlyAccess()));
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
