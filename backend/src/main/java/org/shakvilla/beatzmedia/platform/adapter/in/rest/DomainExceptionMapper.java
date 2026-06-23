package org.shakvilla.beatzmedia.platform.adapter.in.rest;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.shakvilla.beatzmedia.platform.domain.ApiError;
import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Maps {@link DomainException} subclasses to the uniform error envelope. No stack traces, SQL, or
 * PII are exposed. Conventions §4 / ADD §9.
 */
@Provider
public class DomainExceptionMapper implements ExceptionMapper<DomainException> {

  /** HTTP 422 — not present in {@link Response.Status}, so referenced by its numeric code. */
  private static final int UNPROCESSABLE_ENTITY = 422;

  @Override
  public Response toResponse(DomainException ex) {
    int status = mapStatus(ex.getErrorCode());
    ApiError error = ApiError.of(ex.getErrorCode(), ex.getMessage(), ex.getField());
    return Response.status(status).entity(new ErrorEnvelope(error)).build();
  }

  private int mapStatus(ErrorCode code) {
    return switch (code) {
      case VALIDATION -> UNPROCESSABLE_ENTITY;
      case NOT_FOUND -> Response.Status.NOT_FOUND.getStatusCode();
      case UNAUTHENTICATED -> Response.Status.UNAUTHORIZED.getStatusCode();
      case UNAUTHORIZED, FEATURE_DISABLED -> Response.Status.FORBIDDEN.getStatusCode();
      case CONFLICT, ILLEGAL_TRANSITION -> Response.Status.CONFLICT.getStatusCode();
      case RATE_LIMITED -> Response.Status.TOO_MANY_REQUESTS.getStatusCode();
      case MAINTENANCE -> Response.Status.SERVICE_UNAVAILABLE.getStatusCode();
      case PAYLOAD_TOO_LARGE -> 413;
      case INTERNAL -> Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
    };
  }
}
