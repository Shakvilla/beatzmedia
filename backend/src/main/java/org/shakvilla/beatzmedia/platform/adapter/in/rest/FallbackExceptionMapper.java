package org.shakvilla.beatzmedia.platform.adapter.in.rest;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.platform.domain.ApiError;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Catch-all mapper for unexpected exceptions. Logs the full error server-side but returns a safe
 * INTERNAL envelope without stack traces, SQL, or PII. Conventions §4 / ADD §9.
 */
@Provider
public class FallbackExceptionMapper implements ExceptionMapper<Exception> {

  private static final Logger LOG = Logger.getLogger(FallbackExceptionMapper.class);

  @Override
  public Response toResponse(Exception ex) {
    LOG.error("Unhandled exception", ex);
    ApiError error = ApiError.of(ErrorCode.INTERNAL, "An unexpected error occurred.");
    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity(new ErrorEnvelope(error))
        .build();
  }
}
