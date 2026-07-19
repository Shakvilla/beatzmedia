package org.shakvilla.beatzmedia.catalog.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.catalog.application.port.in.AcceptSplitInvite;
import org.shakvilla.beatzmedia.catalog.application.port.in.DeclineSplitInvite;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetSplitInvite;
import org.shakvilla.beatzmedia.catalog.application.port.in.ResendSplitInvites;
import org.shakvilla.beatzmedia.catalog.application.port.in.SplitInviteView;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistProfile;
import org.shakvilla.beatzmedia.catalog.domain.InviteOutcome;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException;
import org.shakvilla.beatzmedia.catalog.domain.SplitInvite;
import org.shakvilla.beatzmedia.catalog.domain.SplitInviteGoneException;
import org.shakvilla.beatzmedia.catalog.domain.SplitInviteIssued;
import org.shakvilla.beatzmedia.catalog.domain.SplitInviteNotFoundException;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.UnauthorizedException;

/**
 * WU-CAT-9 collaborator split invite/accept. Token model mirrors identity's password-reset:
 * only the SHA-256 hash is stored. Accept links the collaborator's account + confirms all their
 * pending splits on the release; decline releases the share to the creator; resend re-issues.
 * Every mutation appends an AuditEntry (INV-10).
 */
@ApplicationScoped
public class SplitInviteService
    implements GetSplitInvite, AcceptSplitInvite, DeclineSplitInvite, ResendSplitInvites {

  private final CatalogRepository repo;
  private final Clock clock;
  private final IdGenerator ids;
  private final AuditWriter auditWriter;
  private final Event<SplitInviteIssued> invites;
  private final long ttlSeconds;
  private final String acceptBaseUrl;

  @Inject
  public SplitInviteService(
      CatalogRepository repo, Clock clock, IdGenerator ids, AuditWriter auditWriter,
      Event<SplitInviteIssued> invites,
      @ConfigProperty(name = "beatz.catalog.split-invite-ttl-seconds", defaultValue = "1209600")
          long ttlSeconds,
      @ConfigProperty(name = "beatz.catalog.split-invite-accept-base-url",
          defaultValue = "http://localhost:5173/studio/splits/accept") String acceptBaseUrl) {
    this.repo = repo;
    this.clock = clock;
    this.ids = ids;
    this.auditWriter = auditWriter;
    this.invites = invites;
    this.ttlSeconds = ttlSeconds;
    this.acceptBaseUrl = acceptBaseUrl;
  }

  @Override
  public SplitInviteView getByToken(String token) {
    SplitInvite invite = repo.findSplitInviteByHash(sha256Hex(token))
        .orElseThrow(SplitInviteNotFoundException::new);
    Release release = repo.findRelease(new ReleaseId(invite.releaseId()))
        .orElseThrow(() -> new ReleaseNotFoundException(invite.releaseId()));
    String artistName = repo.findArtist(new ArtistId(release.getArtistId()))
        .map(ArtistProfile::getName).orElse("");
    List<SplitInviteView.TrackShareView> shares = collaboratorShares(release, invite.email());
    return new SplitInviteView(status(invite), artistName, release.getTitle(), shares);
  }

  @Override
  @Transactional
  public void accept(String token, String accountId) {
    SplitInvite invite = requireLive(token);
    Instant now = clock.now();
    repo.confirmSplitsForReleaseEmail(new ReleaseId(invite.releaseId()), invite.email(), accountId);
    repo.consumeSplitInvite(invite.tokenHash(), InviteOutcome.accepted, now);
    audit(accountId, "ACCEPT_SPLIT_INVITE", invite.releaseId(), now);
  }

  @Override
  @Transactional
  public void decline(String token) {
    SplitInvite invite = requireLive(token);
    Instant now = clock.now();
    repo.declineSplitsForReleaseEmail(new ReleaseId(invite.releaseId()), invite.email());
    repo.consumeSplitInvite(invite.tokenHash(), InviteOutcome.declined, now);
    audit(invite.email(), "DECLINE_SPLIT_INVITE", invite.releaseId(), now);
  }

  @Override
  @Transactional
  public void resend(ReleaseId releaseId, ArtistId requestingArtist) {
    Release release = repo.findRelease(releaseId)
        .orElseThrow(() -> new ReleaseNotFoundException(releaseId.value()));
    if (!release.getArtistId().equals(requestingArtist.value())) {
      throw new UnauthorizedException("Not your release");
    }
    issueInvitesForPending(release);
    audit(requestingArtist.value(), "RESEND_SPLIT_INVITES", releaseId.value(), clock.now());
  }

  /**
   * Mint one fresh invite per collaborator with pending splits on the release + fire the email
   * event. Shared by submit (WU-CAT-9 hook) and resend. Any prior unconsumed invite for that
   * collaborator is deleted first (resend supersedes). MUST run inside a transaction.
   */
  public void issueInvitesForPending(Release release) {
    ReleaseId releaseId = new ReleaseId(release.getId());
    List<String> emails = repo.pendingSplitEmailsForRelease(releaseId);
    if (emails.isEmpty()) return;
    String artistName = repo.findArtist(new ArtistId(release.getArtistId()))
        .map(ArtistProfile::getName).orElse("");
    Instant now = clock.now();
    for (String email : emails) {
      repo.deleteUnconsumedInvitesForReleaseEmail(releaseId, email);
      String plaintext = ids.newId() + ids.newId();
      SplitInvite invite = SplitInvite.issue(
          ids.newId(), release.getId(), email, sha256Hex(plaintext),
          now.plus(Duration.ofSeconds(ttlSeconds)), now);
      repo.saveSplitInvite(invite);
      String acceptUrl = acceptBaseUrl + "?token=" + plaintext;
      invites.fire(new SplitInviteIssued(
          email, acceptUrl, artistName, release.getTitle(), collaboratorShares(release, email).stream()
              .map(s -> new SplitInviteIssued.TrackShare(s.trackTitle(), s.role(), s.percent()))
              .toList()));
    }
  }

  private SplitInvite requireLive(String token) {
    SplitInvite invite = repo.findSplitInviteByHash(sha256Hex(token))
        .orElseThrow(SplitInviteNotFoundException::new);
    if (invite.isConsumed()) {
      throw new SplitInviteGoneException("Split invite already used");
    }
    if (invite.isExpired(clock.now())) {
      throw new SplitInviteGoneException("Split invite expired");
    }
    return invite;
  }

  private List<SplitInviteView.TrackShareView> collaboratorShares(Release release, String email) {
    List<String> trackIds = release.getTracks().stream().map(t -> t.trackId()).toList();
    List<Track> tracks = repo.tracksByIds(trackIds);
    // Re-derive this collaborator's per-track share from persisted splits via findRelease's splits.
    return release.getSplits().stream()
        .filter(s -> s.email().equals(email))
        .map(s -> new SplitInviteView.TrackShareView(
            tracks.stream().filter(t -> t.getId().value().equals(s.trackId())).map(Track::getTitle)
                .findFirst().orElse(""),
            s.role(), s.percent()))
        .toList();
  }

  private String status(SplitInvite invite) {
    if (invite.outcome() == InviteOutcome.accepted) return "accepted";
    if (invite.outcome() == InviteOutcome.declined) return "declined";
    if (invite.isExpired(clock.now())) return "expired";
    return "pending";
  }

  private void audit(String actor, String action, String releaseId, Instant now) {
    auditWriter.append(new AuditEntry(
        ids.newId(), actor, action, "Release", releaseId, AuditType.CATALOG, null, now));
  }

  private static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
