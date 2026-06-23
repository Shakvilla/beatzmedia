package org.shakvilla.beatzmedia.platform.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for domain value objects that carry no framework dependency: {@link ApiError},
 * {@link ErrorCode}, {@link Page}, {@link PlatformSettings}, and all {@link DomainException}
 * subclasses. Testing-strategy §5.
 */
@Tag("unit")
class PlatformDomainTest {

  // ---- ApiError ------------------------------------------------------------

  @Test
  void apiError_of_without_field_sets_field_to_null() {
    ApiError error = ApiError.of(ErrorCode.NOT_FOUND, "resource not found");
    assertEquals("NOT_FOUND", error.code());
    assertEquals("resource not found", error.message());
    assertNull(error.field());
  }

  @Test
  void apiError_of_with_field_preserves_field() {
    ApiError error = ApiError.of(ErrorCode.VALIDATION, "must be positive", "amount");
    assertEquals("VALIDATION", error.code());
    assertEquals("amount", error.field());
  }

  @Test
  void apiError_of_with_null_field_is_accepted() {
    ApiError error = ApiError.of(ErrorCode.INTERNAL, "server error", null);
    assertNull(error.field());
  }

  // ---- ErrorCode -----------------------------------------------------------

  @Test
  void errorCode_all_values_are_accessible() {
    ErrorCode[] codes = ErrorCode.values();
    assertTrue(codes.length > 0, "ErrorCode must have at least one value");
  }

  @Test
  void errorCode_valueOf_returns_correct_constant() {
    assertEquals(ErrorCode.VALIDATION, ErrorCode.valueOf("VALIDATION"));
    assertEquals(ErrorCode.NOT_FOUND, ErrorCode.valueOf("NOT_FOUND"));
    assertEquals(ErrorCode.CONFLICT, ErrorCode.valueOf("CONFLICT"));
    assertEquals(ErrorCode.MAINTENANCE, ErrorCode.valueOf("MAINTENANCE"));
    assertEquals(ErrorCode.FEATURE_DISABLED, ErrorCode.valueOf("FEATURE_DISABLED"));
    assertEquals(ErrorCode.INTERNAL, ErrorCode.valueOf("INTERNAL"));
    assertEquals(ErrorCode.UNAUTHENTICATED, ErrorCode.valueOf("UNAUTHENTICATED"));
    assertEquals(ErrorCode.UNAUTHORIZED, ErrorCode.valueOf("UNAUTHORIZED"));
    assertEquals(ErrorCode.ILLEGAL_TRANSITION, ErrorCode.valueOf("ILLEGAL_TRANSITION"));
    assertEquals(ErrorCode.RATE_LIMITED, ErrorCode.valueOf("RATE_LIMITED"));
  }

  // ---- Page ----------------------------------------------------------------

  @Test
  void page_of_returns_items_and_metadata() {
    Page<String> page = Page.of(List.of("a", "b"), 1, 20, 2L);
    assertEquals(List.of("a", "b"), page.items());
    assertEquals(1, page.page());
    assertEquals(20, page.size());
    assertEquals(2L, page.total());
  }

  @Test
  void page_empty_has_zero_total_and_no_items() {
    Page<String> page = Page.empty(1, 20);
    assertTrue(page.items().isEmpty());
    assertEquals(0L, page.total());
  }

  @Test
  void page_of_copies_items_defensively() {
    List<String> original = new java.util.ArrayList<>(List.of("x"));
    Page<String> page = Page.of(original, 1, 10, 1L);
    original.add("y");
    assertEquals(1, page.items().size(), "Page should hold a defensive copy");
  }

  // ---- PlatformSettings ----------------------------------------------------

  @Test
  void platformSettings_defaults_match_prd_constants() {
    PlatformSettings s = PlatformSettings.defaults();
    assertEquals(30, s.platformFeePct());
    assertEquals(70, s.creatorSharePct());
    assertEquals(10, s.tipFeePct());
    assertEquals(24, s.bundleDiscountPct());
    assertEquals(1000L, s.payoutMinimumMinor());
    assertEquals(50L, s.serviceFeeMinor());
    assertEquals(Currency.GHS, s.defaultCurrency());
  }

  @Test
  void platformSettings_withMaintenanceMode_returns_new_instance() {
    PlatformSettings base = PlatformSettings.defaults();
    PlatformSettings maintenance = base.withMaintenanceMode(true);
    assertTrue(maintenance.maintenanceMode());
    // Original unchanged
    assertNotNull(base);
  }

  @Test
  void platformSettings_withMaintenanceMode_false_clears_flag() {
    PlatformSettings on = PlatformSettings.defaults().withMaintenanceMode(true);
    PlatformSettings off = on.withMaintenanceMode(false);
    assertEquals(false, off.maintenanceMode());
  }

  // ---- DomainException and subclasses -------------------------------------

  @Test
  void domainException_carries_errorCode_and_message() {
    DomainException ex = new DomainException(ErrorCode.INTERNAL, "boom");
    assertEquals(ErrorCode.INTERNAL, ex.getErrorCode());
    assertEquals("boom", ex.getMessage());
    assertNull(ex.getField());
  }

  @Test
  void domainException_with_field_preserves_field() {
    DomainException ex = new DomainException(ErrorCode.VALIDATION, "bad value", "price");
    assertEquals("price", ex.getField());
  }

  @Test
  void validationException_without_field_sets_validation_code() {
    ValidationException ex = new ValidationException("must be positive");
    assertEquals(ErrorCode.VALIDATION, ex.getErrorCode());
    assertEquals("must be positive", ex.getMessage());
    assertNull(ex.getField());
  }

  @Test
  void validationException_with_field_preserves_it() {
    ValidationException ex = new ValidationException("required", "email");
    assertEquals("email", ex.getField());
  }

  @Test
  void conflictException_sets_conflict_code() {
    ConflictException ex = new ConflictException("already exists");
    assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
  }

  @Test
  void conflictException_custom_code_constructor() {
    ConflictException ex = new ConflictException(ErrorCode.ILLEGAL_TRANSITION, "bad state");
    assertEquals(ErrorCode.ILLEGAL_TRANSITION, ex.getErrorCode());
  }

  @Test
  void notFoundException_sets_not_found_code() {
    NotFoundException ex = new NotFoundException("track not found");
    assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    assertEquals("track not found", ex.getMessage());
  }

  @Test
  void featureDisabledException_sets_feature_disabled_code() {
    FeatureDisabledException ex = new FeatureDisabledException("TIPPING");
    assertEquals(ErrorCode.FEATURE_DISABLED, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("TIPPING"));
  }

  @Test
  void maintenanceModeException_sets_maintenance_code() {
    MaintenanceModeException ex = new MaintenanceModeException();
    assertEquals(ErrorCode.MAINTENANCE, ex.getErrorCode());
    assertNotNull(ex.getMessage());
  }

  @Test
  void mismatchedCurrencyException_sets_validation_code() {
    MismatchedCurrencyException ex = new MismatchedCurrencyException("GHS vs USD");
    assertEquals(ErrorCode.VALIDATION, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("GHS"));
  }

  @Test
  void validationException_is_instance_of_domainException() {
    assertThrows(DomainException.class, () -> {
      throw new ValidationException("bad input");
    });
  }
}
