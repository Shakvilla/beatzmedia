package org.shakvilla.beatzmedia.platform.adapter.in.rest;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.platform.domain.ApiError;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Maps JAX-RS framework exceptions (unknown route → 404, wrong method → 405, malformed body → 400,
 * etc.) to the uniform error envelope while <em>preserving their HTTP status</em>.
 *
 * <p>Without this, {@link FallbackExceptionMapper} ({@code ExceptionMapper<Exception>}) would catch
 * every {@link WebApplicationException} and force it to 500, so a missing resource or a wrong method
 * returned 500 instead of 404/405 — a uniform-error-model violation (conventions §4 /
 * API-CONTRACT.md §1). This mapper is more specific than the catch-all, so the runtime prefers it
 * for {@link WebApplicationException} subtypes; genuinely unexpected exceptions still fall through
 * to {@link FallbackExceptionMapper}.
 *
 * <p>The envelope message is derived from the status, never the (possibly detail-leaking) exception
 * message. ADD §9.
 */
@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {

  private static final Logger LOG = Logger.getLogger(WebApplicationExceptionMapper.class);

  @Override
  public Response toResponse(WebApplicationException ex) {
    int status = ex.getResponse() != null ? ex.getResponse().getStatus() : 500;
    ErrorCode code = codeForStatus(status);
    // 5xx are unexpected (server-side); log with the cause. 4xx are client errors; keep quiet.
    if (status >= 500) {
      LOG.error("WebApplicationException (server error)", ex);
    }
    ApiError error = ApiError.of(code, messageForStatus(status));
    return Response.status(status).entity(new ErrorEnvelope(error)).build();
  }

  private ErrorCode codeForStatus(int status) {
    return switch (status) {
      case 400 -> ErrorCode.VALIDATION;
      case 401 -> ErrorCode.UNAUTHENTICATED;
      case 403 -> ErrorCode.UNAUTHORIZED;
      case 404 -> ErrorCode.NOT_FOUND;
      case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
      case 406, 415 -> ErrorCode.UNSUPPORTED_FORMAT;
      case 409 -> ErrorCode.CONFLICT;
      case 413 -> ErrorCode.PAYLOAD_TOO_LARGE;
      case 429 -> ErrorCode.RATE_LIMITED;
      case 503 -> ErrorCode.MAINTENANCE;
      default -> status >= 500 ? ErrorCode.INTERNAL : ErrorCode.VALIDATION;
    };
  }

  private String messageForStatus(int status) {
    return switch (status) {
      case 400 -> "Bad request.";
      case 401 -> "Authentication required.";
      case 403 -> "Not permitted.";
      case 404 -> "Resource not found.";
      case 405 -> "Method not allowed.";
      case 406, 415 -> "Unsupported media type.";
      case 409 -> "Conflict.";
      case 413 -> "Payload too large.";
      case 429 -> "Too many requests.";
      case 503 -> "Service unavailable.";
      default -> status >= 500 ? "An unexpected error occurred." : "Request could not be processed.";
    };
  }
}
