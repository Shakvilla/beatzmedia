package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.shakvilla.beatzmedia.admin.domain.BlankReplyException;
import org.shakvilla.beatzmedia.admin.domain.InvalidTicketStatusException;
import org.shakvilla.beatzmedia.admin.domain.TicketAlreadyResolvedException;
import org.shakvilla.beatzmedia.admin.domain.TicketNotFoundException;
import org.shakvilla.beatzmedia.platform.adapter.in.rest.ErrorEnvelope;
import org.shakvilla.beatzmedia.platform.domain.ApiError;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Maps admin/support domain exceptions to the uniform error envelope {@code { "error": { "code",
 * "message" } }}. Domain types are framework-free (plain {@code RuntimeException} subclasses).
 * Registered as one {@code @Provider} per concrete exception type rather than a single mapper on
 * {@code RuntimeException} — JAX-RS resolves competing mappers registered for the exact same type
 * non-deterministically (another module, {@code LibraryExceptionMapper}, already claims {@code
 * RuntimeException}), which silently fell through to the 500 fallback mapper. Admin ADD §9.
 */
public final class AdminSupportExceptionMapper {

  private AdminSupportExceptionMapper() {}

  @Provider
  public static class TicketNotFoundMapper implements ExceptionMapper<TicketNotFoundException> {
    @Override
    public Response toResponse(TicketNotFoundException ex) {
      return errorResponse(Response.Status.NOT_FOUND, ErrorCode.NOT_FOUND, ex.getMessage());
    }
  }

  @Provider
  public static class TicketAlreadyResolvedMapper
      implements ExceptionMapper<TicketAlreadyResolvedException> {
    @Override
    public Response toResponse(TicketAlreadyResolvedException ex) {
      return errorResponse(Response.Status.CONFLICT, ErrorCode.ILLEGAL_TRANSITION, ex.getMessage());
    }
  }

  @Provider
  public static class BlankReplyMapper implements ExceptionMapper<BlankReplyException> {
    @Override
    public Response toResponse(BlankReplyException ex) {
      return errorResponse(422, ErrorCode.VALIDATION, ex.getMessage());
    }
  }

  @Provider
  public static class InvalidTicketStatusMapper
      implements ExceptionMapper<InvalidTicketStatusException> {
    @Override
    public Response toResponse(InvalidTicketStatusException ex) {
      return errorResponse(422, ErrorCode.VALIDATION, ex.getMessage());
    }
  }

  private static Response errorResponse(Response.Status status, ErrorCode code, String message) {
    return Response.status(status).entity(new ErrorEnvelope(ApiError.of(code, message))).build();
  }

  private static Response errorResponse(int statusCode, ErrorCode code, String message) {
    return Response.status(statusCode).entity(new ErrorEnvelope(ApiError.of(code, message))).build();
  }
}
