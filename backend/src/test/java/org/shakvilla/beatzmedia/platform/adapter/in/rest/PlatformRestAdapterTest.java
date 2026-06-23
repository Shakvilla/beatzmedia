package org.shakvilla.beatzmedia.platform.adapter.in.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.domain.ConflictException;
import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;
import org.shakvilla.beatzmedia.platform.domain.FeatureDisabledException;
import org.shakvilla.beatzmedia.platform.domain.MaintenanceModeException;
import org.shakvilla.beatzmedia.platform.domain.MismatchedCurrencyException;
import org.shakvilla.beatzmedia.platform.domain.NotFoundException;
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
  void domainMapper_internal_returns_500() {
    DomainException ex = new DomainException(ErrorCode.INTERNAL, "unexpected");
    try (Response r = domainMapper.toResponse(ex)) {
      assertEquals(500, r.getStatus());
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

  private static void assertNull(Object value) {
    assertEquals(null, value);
  }
}
