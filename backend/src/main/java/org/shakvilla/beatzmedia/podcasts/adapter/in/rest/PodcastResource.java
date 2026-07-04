package org.shakvilla.beatzmedia.podcasts.adapter.in.rest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;
import org.shakvilla.beatzmedia.podcasts.application.port.in.GetEpisodeStreamUrl;
import org.shakvilla.beatzmedia.podcasts.application.port.in.GetPodcast;
import org.shakvilla.beatzmedia.podcasts.application.port.in.ListEpisodes;
import org.shakvilla.beatzmedia.podcasts.application.port.in.ListPodcasts;
import org.shakvilla.beatzmedia.podcasts.application.port.in.PodcastEpisodeView;
import org.shakvilla.beatzmedia.podcasts.application.port.in.PodcastView;
import org.shakvilla.beatzmedia.podcasts.application.port.in.StreamUrlResult;
import org.shakvilla.beatzmedia.podcasts.application.port.in.TipMethod;
import org.shakvilla.beatzmedia.podcasts.application.port.in.TipShow;
import org.shakvilla.beatzmedia.podcasts.domain.EpisodeId;
import org.shakvilla.beatzmedia.podcasts.domain.MissingIdempotencyKeyException;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastCategory;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastId;
import org.shakvilla.beatzmedia.podcasts.domain.TipResult;

import io.quarkus.security.Authenticated;

/**
 * Thin REST resource for the podcasts browse/detail/gated-stream endpoints (LLFR-PODCAST-01.1 –
 * 01.3). Maps HTTP to input ports; no business logic — the INV-3 rendition decision lives entirely
 * in {@code GetEpisodeStreamUrlService}. Podcasts ADD §5.1 / API-CONTRACT.md §8.
 *
 * <ul>
 *   <li>GET /v1/podcasts?category=&amp;page=&amp;size= → Page&lt;PodcastDto&gt; (200)
 *   <li>GET /v1/podcasts/:id → PodcastDto (200); 404 NOT_FOUND
 *   <li>GET /v1/podcasts/:id/episodes → PodcastEpisodeDto[] (200); 404 NOT_FOUND
 *   <li>GET /v1/podcasts/episodes/:id/stream → StreamUrlResponse (200); 404 NOT_FOUND
 * </ul>
 *
 * <p>Auth is optional on every read (PRD §6.8 / ADD §9): an anonymous caller gets
 * {@code isOwned=false} decoration and a preview stream for gated episodes; an authenticated
 * caller's identity is always derived from the verified JWT subject — never a client-supplied
 * body/query field. The stream endpoint is namespaced under {@code /podcasts/episodes/:id/stream}
 * (not {@code /episodes/:id/stream}) to keep a single {@code @Path("/v1")} root without a
 * structural route collision against {@code /podcasts/:id} (WU-PLY-1 routing lesson).
 */
@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class PodcastResource {

  private final ListPodcasts listPodcasts;
  private final GetPodcast getPodcast;
  private final ListEpisodes listEpisodes;
  private final GetEpisodeStreamUrl getEpisodeStreamUrl;
  private final TipShow tipShow;
  private final PodcastTipRateLimiter tipRateLimiter;
  private final JsonWebToken jwt;

  @Inject
  public PodcastResource(
      ListPodcasts listPodcasts,
      GetPodcast getPodcast,
      ListEpisodes listEpisodes,
      GetEpisodeStreamUrl getEpisodeStreamUrl,
      TipShow tipShow,
      PodcastTipRateLimiter tipRateLimiter,
      JsonWebToken jwt) {
    this.listPodcasts = listPodcasts;
    this.getPodcast = getPodcast;
    this.listEpisodes = listEpisodes;
    this.getEpisodeStreamUrl = getEpisodeStreamUrl;
    this.tipShow = tipShow;
    this.tipRateLimiter = tipRateLimiter;
    this.jwt = jwt;
  }

  /** GET /v1/podcasts?category=&page=&size= — LLFR-PODCAST-01.1. */
  @GET
  @Path("/podcasts")
  public Page<PodcastView> listPodcasts(
      @QueryParam("category") String category,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("size") @DefaultValue("20") int size) {
    Optional<PodcastCategory> parsedCategory = parseCategory(category);
    return listPodcasts.list(parsedCategory, new PageRequest(page, size));
  }

  /** GET /v1/podcasts/:id — LLFR-PODCAST-01.2. */
  @GET
  @Path("/podcasts/{id}")
  public PodcastView getPodcast(@PathParam("id") String id) {
    return getPodcast.get(new PodcastId(id));
  }

  /** GET /v1/podcasts/:id/episodes — LLFR-PODCAST-01.3. */
  @GET
  @Path("/podcasts/{id}/episodes")
  public List<PodcastEpisodeView> listEpisodes(@PathParam("id") String id) {
    return listEpisodes.list(new PodcastId(id), callerId());
  }

  /** GET /v1/podcasts/episodes/:id/stream — LLFR-PODCAST-01.3 (INV-3 gated stream). */
  @GET
  @Path("/podcasts/episodes/{id}/stream")
  public StreamUrlResponse getEpisodeStreamUrl(@PathParam("id") String id) {
    StreamUrlResult result = getEpisodeStreamUrl.getStreamUrl(new EpisodeId(id), callerId());
    return new StreamUrlResponse(
        result.audioUrl(), result.previewSeconds().orElse(null), result.expiresAt());
  }

  /**
   * POST /v1/podcasts/:id/tip — a fan tips a show, credited 90/10 via payments (LLFR-PODCAST-02.1).
   *
   * <p>Thin: it maps the DTO → command and calls {@link TipShow}, which resolves the show → creator
   * server-side and delegates the money movement to payments' {@code IssueTip} pipeline. Guards
   * enforced here at the boundary:
   *
   * <ul>
   *   <li><strong>Auth:</strong> {@code @Authenticated}; the tipping fan is the JWT subject, never a
   *       body field — a client cannot tip on behalf of another account;
   *   <li><strong>Idempotency (INV-1):</strong> the {@code Idempotency-Key} header is mandatory —
   *       missing ⇒ 400 {@code MISSING_IDEMPOTENCY_KEY}; the same key ⇒ one charge, same result;
   *   <li><strong>Rate limiting:</strong> per-account token bucket (security-authz §6) → 429 +
   *       {@code Retry-After} on abusive bursts, checked BEFORE any charge is initiated;
   *   <li><strong>Amount bounds:</strong> decimal cedis → minor units happens only here (INV-11); a
   *       non-positive OR overflowing amount is rejected as {@code VALIDATION} (422), never an
   *       unmapped 500; the platform charge ceiling is enforced in payments ({@code IssueTipService})
   *       → {@code CHARGE_AMOUNT_EXCEEDED} (422).
   * </ul>
   *
   * <p>Returns 202 Accepted: the charge is in flight and no value has moved (INV-1) — the 90/10 split
   * (INV-4, {@code PlatformSettings.tipFeePct}, OQ-2 default 10%) posts on settlement.
   */
  @POST
  @Path("/podcasts/{id}/tip")
  @Consumes(MediaType.APPLICATION_JSON)
  @Authenticated
  public Response tip(
      @PathParam("id") String id,
      @HeaderParam("Idempotency-Key") String idempotencyKey,
      TipRequest req) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new MissingIdempotencyKeyException();
    }
    if (req == null) {
      throw new ValidationException("request body is required");
    }

    AccountId fan = new AccountId(jwt.getSubject());
    // Per-account rate limit on the money path (429 + Retry-After) BEFORE any charge.
    tipRateLimiter.check(fan.value());
    Money amount = parseAmount(req.amount(), req.currency());
    TipMethod method =
        new TipMethod(
            requireField(req.provider(), "provider"),
            requireField(req.methodKind(), "methodKind"),
            requireField(req.paymentToken(), "paymentToken"));

    TipResult result = tipShow.tip(new PodcastId(id), fan, amount, method, idempotencyKey);
    return Response.status(Response.Status.ACCEPTED).entity(TipResponse.from(result)).build();
  }

  private static String requireField(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new ValidationException(field + " is required", field);
    }
    return value;
  }

  private static Money parseAmount(BigDecimal amount, String currency) {
    if (amount == null) {
      throw new ValidationException("amount is required", "amount");
    }
    if (amount.signum() <= 0) {
      throw new ValidationException("tip amount must be positive", "amount");
    }
    Currency ccy =
        (currency != null && !currency.isBlank()) ? parseCurrency(currency) : Currency.GHS;
    // Decimal cedis → minor units happens only here, at the boundary (INV-11). An amount too large to
    // fit a long overflows in Money.ofCedis (BigDecimal.longValueExact → ArithmeticException); catch
    // it and surface a mapped 422 VALIDATION rather than letting it fall through to an unmapped 500.
    // The absolute charge ceiling (< long) is then enforced in payments (CHARGE_AMOUNT_EXCEEDED).
    try {
      return Money.ofCedis(amount, ccy);
    } catch (ArithmeticException e) {
      throw new ValidationException("tip amount is too large", "amount");
    }
  }

  private static Currency parseCurrency(String value) {
    try {
      return Currency.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ValidationException("unsupported currency: " + value, "amount.currency");
    }
  }

  private Optional<PodcastCategory> parseCategory(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(PodcastCategory.fromWireValue(raw));
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Invalid podcast category: " + raw, "category");
    }
  }

  /** Extract caller account id from JWT sub, if a valid token is present. */
  private Optional<AccountId> callerId() {
    try {
      String sub = jwt.getSubject();
      return (sub != null && !sub.isBlank()) ? Optional.of(new AccountId(sub)) : Optional.empty();
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
