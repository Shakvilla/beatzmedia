package org.shakvilla.beatzmedia.platform.adapter.in.rest;

import java.lang.reflect.Method;
import java.util.Set;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import org.shakvilla.beatzmedia.platform.application.port.out.FeatureFlags;
import org.shakvilla.beatzmedia.platform.application.port.out.PlatformSettingsProvider;
import org.shakvilla.beatzmedia.platform.domain.ApiError;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * JAX-RS filter that enforces platform-level rules before any resource dispatches:
 *
 * <ol>
 *   <li>If {@code maintenanceMode=true} and the request is a non-admin write
 *       (POST/PUT/PATCH/DELETE), return 503 MAINTENANCE.
 *   <li>If the endpoint is annotated with {@link RequiresFeature} and the flag is disabled, return
 *       403 FEATURE_DISABLED.
 * </ol>
 *
 * ADD §5.2 / LLFR-PLATFORM-01.1.
 */
@Provider
@ApplicationScoped
@Priority(Priorities.AUTHORIZATION - 10)
public class PlatformEnforcementFilter implements ContainerRequestFilter {

  private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

  @Inject
  PlatformSettingsProvider settingsProvider;

  @Inject
  FeatureFlags featureFlags;

  @Context
  ResourceInfo resourceInfo;

  @Override
  public void filter(ContainerRequestContext ctx) {
    // 1. Maintenance mode check: block non-admin writes.
    if (settingsProvider.current().maintenanceMode()) {
      String method = ctx.getMethod();
      if (WRITE_METHODS.contains(method) && !isAdminPath(ctx)) {
        ctx.abortWith(buildError(
            Response.Status.SERVICE_UNAVAILABLE,
            ErrorCode.MAINTENANCE,
            "The platform is currently under maintenance. Please try again later.",
            null));
        return;
      }
    }

    // 2. Feature flag check: method annotation overrides class annotation.
    RequiresFeature featureAnnotation = resolveFeatureAnnotation(resourceInfo);
    if (featureAnnotation != null) {
      if (!featureFlags.isEnabled(featureAnnotation.value())) {
        ctx.abortWith(buildError(
            Response.Status.FORBIDDEN,
            ErrorCode.FEATURE_DISABLED,
            "Feature is currently disabled: " + featureAnnotation.value().name(),
            null));
      }
    }
  }

  /** Resolve @RequiresFeature: method annotation takes priority over class annotation. */
  private RequiresFeature resolveFeatureAnnotation(ResourceInfo ri) {
    if (ri == null) {
      return null;
    }
    Method method = ri.getResourceMethod();
    if (method != null) {
      RequiresFeature methodAnnotation = method.getAnnotation(RequiresFeature.class);
      if (methodAnnotation != null) {
        return methodAnnotation;
      }
    }
    Class<?> clazz = ri.getResourceClass();
    if (clazz != null) {
      return clazz.getAnnotation(RequiresFeature.class);
    }
    return null;
  }

  /** Admin paths bypass maintenance mode for writes (admin must still be able to disable it). */
  private boolean isAdminPath(ContainerRequestContext ctx) {
    String path = ctx.getUriInfo().getPath();
    return path != null && path.startsWith("/v1/admin");
  }

  private Response buildError(Response.Status status, ErrorCode code, String message, String field) {
    ApiError error = ApiError.of(code, message, field);
    ErrorEnvelope envelope = new ErrorEnvelope(error);
    return Response.status(status).entity(envelope).build();
  }
}
