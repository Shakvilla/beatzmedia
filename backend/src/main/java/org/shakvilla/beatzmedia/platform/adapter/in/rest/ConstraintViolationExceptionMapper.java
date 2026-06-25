package org.shakvilla.beatzmedia.platform.adapter.in.rest;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.shakvilla.beatzmedia.platform.domain.ApiError;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Maps {@link ConstraintViolationException} (Hibernate Validator / Bean Validation) to the uniform
 * error envelope with HTTP 422 and {@code error.field} pointing to the first violated path.
 * Conventions §4 / §5 — validation failures use the VALIDATION code and carry a field pointer.
 *
 * <p>Added for WU-IDN-1: signup/login request DTOs carry {@code @NotBlank} / {@code @Email}
 * constraints that produce {@link ConstraintViolationException}. The platform FallbackExceptionMapper
 * would otherwise return a non-envelope 500.
 */
@Provider
public class ConstraintViolationExceptionMapper
    implements ExceptionMapper<ConstraintViolationException> {

  private static final int UNPROCESSABLE_ENTITY = 422;

  @Override
  public Response toResponse(ConstraintViolationException ex) {
    String field = null;
    String message = "Validation failed.";

    if (ex.getConstraintViolations() != null && !ex.getConstraintViolations().isEmpty()) {
      var violation = ex.getConstraintViolations().iterator().next();
      field = extractField(violation.getPropertyPath().toString());
      message = violation.getMessage();
    }

    ApiError error = ApiError.of(ErrorCode.VALIDATION, message, field);
    return Response.status(UNPROCESSABLE_ENTITY).entity(new ErrorEnvelope(error)).build();
  }

  /**
   * Strips the method/parameter prefix from the property path, leaving only the field name.
   * E.g. {@code "signup.signupRequest.email"} → {@code "email"}.
   */
  private String extractField(String propertyPath) {
    if (propertyPath == null || propertyPath.isBlank()) {
      return null;
    }
    int lastDot = propertyPath.lastIndexOf('.');
    return lastDot >= 0 ? propertyPath.substring(lastDot + 1) : propertyPath;
  }
}
