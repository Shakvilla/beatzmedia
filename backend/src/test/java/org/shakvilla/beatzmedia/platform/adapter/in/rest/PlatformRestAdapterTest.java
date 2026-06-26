package org.shakvilla.beatzmedia.platform.adapter.in.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.media.domain.FileRejectedException;
import org.shakvilla.beatzmedia.media.domain.UnsupportedFormatException;
import org.shakvilla.beatzmedia.platform.domain.ConflictException;
import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;
import org.shakvilla.beatzmedia.platform.domain.FeatureDisabledException;
import org.shakvilla.beatzmedia.platform.domain.MaintenanceModeException;
import org.shakvilla.beatzmedia.platform.domain.MismatchedCurrencyException;
import org.shakvilla.beatzmedia.platform.domain.NotFoundException;
import org.shakvilla.beatzmedia.platform.domain.RateLimitedException;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * Pure unit tests for the platform REST adapter classes. The mappers and envelope are
 * framework-free enough to be instantiated directly — no Quarkus context needed.
 * Testing-strategy §5 / ADD §9.
 */
@Tag("unit")
class PlatformRestAdapterTest {

  private final DomainExceptionMapper domainMapper = new DomainExceptionMapper();
  private final FallbackExceptionMapper fallbackMapper = new FallbackExceptionMapper();
  private final WebApplicationExceptionMapper webMapper = new WebApplicationExceptionMapper();

  // ---- ErrorEnvelope -------------------------------------------------------

  @Test
  void errorEnvelope_wraps_apiError() {
    org.shakvilla.beatzmedia.platform.domain.ApiError apiError =
        org.shakvilla.beatzmedia.platform.domain.ApiError.of(ErrorCode.NOT_FOUND, "not found");
    ErrorEnvelope envelope = new ErrorEnvelope(apiError);
    assertEquals(apiError, envelope.error());
    assertEquals("NOT_FOUND", envelope.error().code());
  }

  // ---- DomainExceptionMapper -----------------------------------------------

  @Test
  void domainMapper_validationException_returns_422() {
    ValidationException ex = new ValidationException("bad value", "price");
    try (Response r = domainMapper.toResponse(ex)) {
      assertEquals(422, r.getStatus());
      ErrorEnvelope body = (ErrorEnvelope) r.getEntity();
      assertNotNull(body);
      assertEquals("VALIDATION", body.error().code());
      assertEquals("price", body.error().field());
    }
  }

  @Test
  void domainMapper_notFoundException_returns_404() {
    NotFoundException ex = new NotFoundException("track not found");
    try (Response r = domainMapper.toResponse(ex)) {
      assertEquals(404, r.getStatus());
      ErrorEnvelope body = (ErrorEnvelope) r.getEntity();
      assertEquals("NOT_FOUND", body.error().code());
    }
  }

  @Test
  void domainMapper_conflictException_returns_409() {
    ConflictException ex = new ConflictException("already exists");
    try (Response r = domainMapper.toResponse(ex)) {
      assertEquals(409, r.getStatus());
      ErrorEnvelope body = (ErrorEnvelope) r.getEntity();
      assertEquals("CONFLICT", body.error().code());
    }
  }

  @Test
  void domainMapper_illegalTransition_returns_409() {
    ConflictException ex = new ConflictException(ErrorCode.ILLEGAL_TRANSITION, "bad transition");
    try (Response r = domainMapper.toResponse(ex)) {
      assertEquals(409, r.getStatus());
    }
  }

  @Test
  void domainMapper_maintenanceModeException_returns_503() {
    MaintenanceModeException ex = new MaintenanceModeException();
    try (Response r = domainMapper.toResponse(ex)) {
      assertEquals(503, r.getStatus());
      ErrorEnvelope body = (ErrorEnvelope) r.getEntity();
      assertEquals("MAINTENANCE", body.error().code());
    }
  }

  @Test
  void domainMapper_featureDisabledException_returns_403() {
    FeatureDisabledException ex = new FeatureDisabledException("TIPPING");
    try (Response r = domainMapper.toResponse(ex)) {
      assertEquals(403, r.getStatus());
      ErrorEnvelope body = (ErrorEnvelope) r.getEntity();
      assertEquals("FEATURE_DISABLED", body.error().code());
    }
  }

  @Test
  void domainMapper_mismatchedCurrency_returns_422() {
    MismatchedCurrencyException ex = new MismatchedCurrencyException("GHS vs USD");
    try (Response r = domainMapper.toResponse(ex)) {
      assertEquals(422, r.getStatus());
    }
  }

  @Test
  void domainMapper_unauthenticated_returns_401() {
    DomainException ex = new DomainException(ErrorCode.UNAUTHENTICATED, "not logged in");
    try (Response r = domainMapper.toResponse(ex)) {
      assertEquals(401, r.getStatus());
    }
  }

  @Test
  void domainMapper_unauthorized_returns_403() {
    DomainException ex = new DomainException(ErrorCode.UNAUTHORIZED, "forbidden");
    try (Response r = domainMapper.toResponse(ex)) {
      assertEquals(403, r.getStatus());
    }
  }

  @Test
  void domainMapper_rateLimited_returns_429() {
    DomainException ex = new DomainException(ErrorCode.RATE_LIMITED, "too many requests");
    try (Response r = domainMapper.toResponse(ex)) {
      assertEquals(429, r.getStatus());
    }
  }

  @Test
  void domainMapper_rateLimitedException_sets_retryAfter_header() {
    RateLimitedException ex = new RateLimitedException("too many login attempts", 30);
    try (Response r = domainMapper.toResponse(ex)) {
      assertEquals(429, r.getStatus());
      ErrorEnvelope body = (ErrorEnvelope) r.getEntity();
      assertEquals("RATE_LIMITED", body.error().code());
      assertEquals("30", r.getHeaderString("Retry-After"));
    }
  }

  @Test
  void domainMapper_rateLimitedException_clamps_negative_retryAfter_to_zero() {
    RateLimitedException ex = new RateLimitedException("too many requests", -5);
    try (Response r = domainMapper.toResponse(ex)) {
      assertEquals("0", r.getHeaderString("Retry-After"));
    }
  }

  @Test
  void domainMapper_plainRateLimited_omits_retryAfter_header() {
    DomainException ex = new DomainException(ErrorCode.RATE_LIMITED, "too many requests");
    try (Response r = domainMapper.toResponse(ex)) {
      assertNull(r.getHeaderString("Retry-After"));
    }
  }

  @Test
  void domainMapper_internal_returns_500() {
    DomainException ex = new DomainException(ErrorCode.INTERNAL, "unexpected");
    try (Response r = domainMapper.toResponse(ex)) {
      assertEquals(500, r.getStatus());
    }
  }

  @Test
  void domainMapper_unsupportedFormat_returns_422_with_exact_code() {
    UnsupportedFormatException ex = new UnsupportedFormatException("EXE is not accepted");
    try (Response r = domainMapper.toResponse(ex)) {
      assertEquals(422, r.getStatus());
      ErrorEnvelope body = (ErrorEnvelope) r.getEntity();
      assertEquals("UNSUPPORTED_FORMAT", body.error().code());
      assertEquals("file", body.error().field());
    }
  }

  @Test
  void domainMapper_fileRejected_returns_422_with_exact_code() {
    FileRejectedException ex = new FileRejectedException("virus detected");
    try (Response r = domainMapper.toResponse(ex)) {
      assertEquals(422, r.getStatus());
      ErrorEnvelope body = (ErrorEnvelope) r.getEntity();
      assertEquals("FILE_REJECTED", body.error().code());
      assertEquals("file", body.error().field());
    }
  }

  @Test
  void domainMapper_preserves_message_in_body() {
    ValidationException ex = new ValidationException("amount must be positive");
    try (Response r = domainMapper.toResponse(ex)) {
      ErrorEnvelope body = (ErrorEnvelope) r.getEntity();
      assertEquals("amount must be positive", body.error().message());
    }
  }

  // ---- FallbackExceptionMapper ---------------------------------------------

  @Test
  void fallbackMapper_returns_500_for_unexpected_exception() {
    RuntimeException ex = new RuntimeException("something blew up");
    try (Response r = fallbackMapper.toResponse(ex)) {
      assertEquals(500, r.getStatus());
      ErrorEnvelope body = (ErrorEnvelope) r.getEntity();
      assertNotNull(body);
      assertEquals("INTERNAL", body.error().code());
    }
  }

  @Test
  void fallbackMapper_message_is_generic_not_exception_detail() {
    RuntimeException ex = new RuntimeException("DB connection refused at 10.0.0.1:5432");
    try (Response r = fallbackMapper.toResponse(ex)) {
      ErrorEnvelope body = (ErrorEnvelope) r.getEntity();
      // Must NOT leak internal details
      assertEquals("An unexpected error occurred.", body.error().message());
    }
  }

  @Test
  void fallbackMapper_field_is_null() {
    try (Response r = fallbackMapper.toResponse(new RuntimeException("err"))) {
      ErrorEnvelope body = (ErrorEnvelope) r.getEntity();
      assertNull(body.error().field());
    }
  }

  // ---- WebApplicationExceptionMapper ---------------------------------------
  // Framework-thrown JAX-RS exceptions (unknown route, wrong method, etc.) must
  // keep their real HTTP status instead of being swallowed into 500 by the
  // catch-all FallbackExceptionMapper. Regression for the 404/405 -> 500 bug.

  @Test
  void webMapper_notFound_returns_404_with_NOT_FOUND_code() {
    try (Response r = webMapper.toResponse(new jakarta.ws.rs.NotFoundException())) {
      assertEquals(404, r.getStatus());
      ErrorEnvelope body = (ErrorEnvelope) r.getEntity();
      assertEquals("NOT_FOUND", body.error().code());
    }
  }

  @Test
  void webMapper_methodNotAllowed_returns_405_with_METHOD_NOT_ALLOWED_code() {
    try (Response r = webMapper.toResponse(new jakarta.ws.rs.NotAllowedException("GET"))) {
      assertEquals(405, r.getStatus());
      ErrorEnvelope body = (ErrorEnvelope) r.getEntity();
      assertEquals("METHOD_NOT_ALLOWED", body.error().code());
    }
  }

  @Test
  void webMapper_badRequest_returns_400_with_VALIDATION_code() {
    try (Response r = webMapper.toResponse(new jakarta.ws.rs.BadRequestException())) {
      assertEquals(400, r.getStatus());
      ErrorEnvelope body = (ErrorEnvelope) r.getEntity();
      assertEquals("VALIDATION", body.error().code());
    }
  }

  @Test
  void webMapper_notAuthorized_returns_401_with_UNAUTHENTICATED_code() {
    try (Response r = webMapper.toResponse(new jakarta.ws.rs.NotAuthorizedException("Bearer"))) {
      assertEquals(401, r.getStatus());
      ErrorEnvelope body = (ErrorEnvelope) r.getEntity();
      assertEquals("UNAUTHENTICATED", body.error().code());
    }
  }

  @Test
  void webMapper_forbidden_returns_403_with_UNAUTHORIZED_code() {
    try (Response r = webMapper.toResponse(new jakarta.ws.rs.ForbiddenException())) {
      assertEquals(403, r.getStatus());
      ErrorEnvelope body = (ErrorEnvelope) r.getEntity();
      assertEquals("UNAUTHORIZED", body.error().code());
    }
  }

  @Test
  void webMapper_serverError_returns_500_with_INTERNAL_code() {
    try (Response r = webMapper.toResponse(new jakarta.ws.rs.InternalServerErrorException())) {
      assertEquals(500, r.getStatus());
      ErrorEnvelope body = (ErrorEnvelope) r.getEntity();
      assertEquals("INTERNAL", body.error().code());
    }
  }

  @Test
  void webMapper_message_is_generic_not_exception_detail() {
    jakarta.ws.rs.NotFoundException ex =
        new jakarta.ws.rs.NotFoundException("no row for id 42 in table accounts");
    try (Response r = webMapper.toResponse(ex)) {
      // Must not echo arbitrary framework/internal detail into the envelope.
      assertEquals("Resource not found.", body(r).error().message());
    }
  }

  private static ErrorEnvelope body(Response r) {
    return (ErrorEnvelope) r.getEntity();
  }

  private static void assertNull(Object value) {
    assertEquals(null, value);
  }
}
