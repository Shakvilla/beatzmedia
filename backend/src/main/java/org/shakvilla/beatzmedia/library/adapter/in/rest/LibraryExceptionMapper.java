package org.shakvilla.beatzmedia.library.adapter.in.rest;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.shakvilla.beatzmedia.library.domain.InvalidTitleException;
import org.shakvilla.beatzmedia.library.domain.PlaylistNotFoundException;
import org.shakvilla.beatzmedia.library.domain.TargetNotFoundException;

/**
 * Uniform error envelope mapper for library domain exceptions. Maps to the API-CONTRACT §1 error
 * shape: {@code { "error": { "code", "message" } }}. Library ADD §9.
 */
@Provider
public class LibraryExceptionMapper
    implements ExceptionMapper<RuntimeException> {

  @Override
  public Response toResponse(RuntimeException ex) {
    if (ex instanceof TargetNotFoundException e) {
      return errorResponse(Response.Status.NOT_FOUND, e.code(), e.getMessage());
    }
    if (ex instanceof PlaylistNotFoundException e) {
      return errorResponse(Response.Status.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }
    if (ex instanceof InvalidTitleException e) {
      return errorResponse(422, "INVALID_TITLE", e.getMessage());
    }
    // Unhandled: re-throw to let the container deal with it
    throw ex;
  }

  private Response errorResponse(Response.Status status, String code, String message) {
    var body = new ErrorEnvelope(new ErrorEnvelope.ErrorDto(code, message, null));
    return Response.status(status).entity(body).build();
  }

  private Response errorResponse(int statusCode, String code, String message) {
    var body = new ErrorEnvelope(new ErrorEnvelope.ErrorDto(code, message, null));
    return Response.status(statusCode).entity(body).build();
  }

  public record ErrorEnvelope(ErrorDto error) {
    public record ErrorDto(String code, String message, String field) {}
  }
}
