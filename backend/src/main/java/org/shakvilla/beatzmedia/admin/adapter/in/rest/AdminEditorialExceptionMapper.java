package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.shakvilla.beatzmedia.admin.domain.BlankFeaturedSlotTitleException;
import org.shakvilla.beatzmedia.admin.domain.BlankPlaylistNameException;
import org.shakvilla.beatzmedia.admin.domain.BlankPushItemFieldException;
import org.shakvilla.beatzmedia.admin.domain.DuplicateFeaturedPositionException;
import org.shakvilla.beatzmedia.platform.adapter.in.rest.ErrorEnvelope;
import org.shakvilla.beatzmedia.platform.domain.ApiError;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Maps admin/editorial domain exceptions to the uniform error envelope {@code { "error": { "code",
 * "message" } }}. Domain types are framework-free (plain {@code RuntimeException} subclasses).
 * Registered as one {@code @Provider} per concrete exception type rather than a single mapper on
 * {@code RuntimeException} — JAX-RS resolves competing mappers registered for the exact same type
 * non-deterministically across modules, which silently falls through to the 500 fallback mapper.
 * Admin ADD §9 (mirrors {@code AdminSupportExceptionMapper}, WU-ADM-7).
 */
public final class AdminEditorialExceptionMapper {

  private AdminEditorialExceptionMapper() {}

  @Provider
  public static class BlankFeaturedSlotTitleMapper
      implements ExceptionMapper<BlankFeaturedSlotTitleException> {
    @Override
    public Response toResponse(BlankFeaturedSlotTitleException ex) {
      return errorResponse(422, ErrorCode.VALIDATION, ex.getMessage());
    }
  }

  @Provider
  public static class BlankPushItemFieldMapper
      implements ExceptionMapper<BlankPushItemFieldException> {
    @Override
    public Response toResponse(BlankPushItemFieldException ex) {
      return errorResponse(422, ErrorCode.VALIDATION, ex.getMessage());
    }
  }

  @Provider
  public static class BlankPlaylistNameMapper implements ExceptionMapper<BlankPlaylistNameException> {
    @Override
    public Response toResponse(BlankPlaylistNameException ex) {
      return errorResponse(422, ErrorCode.VALIDATION, ex.getMessage());
    }
  }

  @Provider
  public static class DuplicateFeaturedPositionMapper
      implements ExceptionMapper<DuplicateFeaturedPositionException> {
    @Override
    public Response toResponse(DuplicateFeaturedPositionException ex) {
      return errorResponse(422, ErrorCode.VALIDATION, ex.getMessage());
    }
  }

  private static Response errorResponse(int statusCode, ErrorCode code, String message) {
    return Response.status(statusCode).entity(new ErrorEnvelope(ApiError.of(code, message))).build();
  }
}
