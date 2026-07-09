package org.shakvilla.beatzmedia.platform.adapter.in.rest;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.shakvilla.beatzmedia.platform.domain.ApiError;
import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;
import org.shakvilla.beatzmedia.platform.domain.RateLimitedException;

/**
 * Maps {@link DomainException} subclasses to the uniform error envelope. No stack traces, SQL, or
 * PII are exposed. Conventions §4 / ADD §9.
 */
@Provider
public class DomainExceptionMapper implements ExceptionMapper<DomainException> {

  /** HTTP 422 — not present in {@link Response.Status}, so referenced by its numeric code. */
  private static final int UNPROCESSABLE_ENTITY = 422;

  /** HTTP 502 — upstream payment provider failure. */
  private static final int BAD_GATEWAY = 502;

  @Override
  public Response toResponse(DomainException ex) {
    int status = mapStatus(ex.getErrorCode());
    ApiError error = ApiError.of(ex.getErrorCode(), ex.getMessage(), ex.getField());
    Response.ResponseBuilder builder = Response.status(status).entity(new ErrorEnvelope(error));
    if (ex instanceof RateLimitedException rateLimited) {
      builder.header("Retry-After", rateLimited.getRetryAfterSeconds());
    }
    return builder.build();
  }

  private int mapStatus(ErrorCode code) {
    return switch (code) {
      case VALIDATION, UNSUPPORTED_FORMAT, FILE_REJECTED, WEAK_PASSWORD, INVALID_ROLE,
              MISSING_QUERY, TRACK_COUNT_INVALID, SPLIT_OVER_100, CHARGE_AMOUNT_EXCEEDED,
              BELOW_MIN_PAYOUT, INVALID_GENRE, INVALID_PRICE, SCHEDULE_DATE_REQUIRED,
              MEDIA_INVALID, INVALID_RANGE ->
          UNPROCESSABLE_ENTITY;
      case NOT_FOUND,
              ARTIST_NOT_FOUND,
              ALBUM_NOT_FOUND,
              TRACK_NOT_FOUND,
              LYRICS_NOT_FOUND,
              PLAYLIST_NOT_FOUND,
              RELEASE_NOT_FOUND,
              PAYMENT_INTENT_NOT_FOUND,
              PAYOUT_METHOD_NOT_FOUND,
              DISPUTE_NOT_FOUND,
              SHOW_NOT_FOUND,
              EPISODE_NOT_FOUND ->
          Response.Status.NOT_FOUND.getStatusCode();
      case UNAUTHENTICATED, INVALID_CREDENTIALS, SOCIAL_TOKEN_INVALID ->
        Response.Status.UNAUTHORIZED.getStatusCode();
      case UNAUTHORIZED, FEATURE_DISABLED, ACCOUNT_SUSPENDED, KYC_REQUIRED, TIPS_DISABLED ->
          Response.Status.FORBIDDEN.getStatusCode();
      case SELF_TIP_NOT_ALLOWED -> UNPROCESSABLE_ENTITY;
      case CONFLICT,
              ILLEGAL_TRANSITION,
              EMAIL_TAKEN,
              LAST_SUPER_ADMIN,
              RELEASE_LIVE,
              IDEMPOTENCY_KEY_CONFLICT,
              ALREADY_OWNED,
              NOT_STACKABLE,
              CART_EMPTY,
              CHECKOUT_KIND_UNSUPPORTED,
              INSUFFICIENT_BALANCE,
              KYC_BLOCKED,
              PAYOUT_METHOD_IN_USE,
              TIER_SOLD_OUT,
              USERNAME_TAKEN,
              EPISODE_PUBLISHED ->
          Response.Status.CONFLICT.getStatusCode();
      case MISSING_IDEMPOTENCY_KEY -> Response.Status.BAD_REQUEST.getStatusCode();
      case PROVIDER_ERROR -> BAD_GATEWAY;
      case METHOD_NOT_ALLOWED -> 405;
      case RATE_LIMITED -> Response.Status.TOO_MANY_REQUESTS.getStatusCode();
      case MAINTENANCE, MEDIA_UNAVAILABLE -> Response.Status.SERVICE_UNAVAILABLE.getStatusCode();
      case PAYLOAD_TOO_LARGE -> 413;
      case INTERNAL -> Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
    };
  }
}
