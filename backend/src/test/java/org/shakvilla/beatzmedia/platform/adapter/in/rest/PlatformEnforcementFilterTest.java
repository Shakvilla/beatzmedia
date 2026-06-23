package org.shakvilla.beatzmedia.platform.adapter.in.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.fakes.FakeFeatureFlags;
import org.shakvilla.beatzmedia.platform.fakes.FakePlatformSettingsProvider;

/**
 * Unit tests for {@link PlatformEnforcementFilter}. Injects fakes via reflection (no Quarkus
 * context needed) and drives the filter directly with a minimal {@link ContainerRequestContext}
 * stand-in. Testing-strategy §5 / ADD §5.2.
 */
@Tag("unit")
class PlatformEnforcementFilterTest {

  private PlatformEnforcementFilter filter;
  private FakePlatformSettingsProvider settingsProvider;
  private FakeFeatureFlags featureFlags;

  @BeforeEach
  void setUp() throws Exception {
    filter = new PlatformEnforcementFilter();
    settingsProvider = new FakePlatformSettingsProvider();
    featureFlags = new FakeFeatureFlags();

    inject(filter, "settingsProvider", settingsProvider);
    inject(filter, "featureFlags", featureFlags);
    // resourceInfo null → resolveFeatureAnnotation returns null (safe null-check path)
    inject(filter, "resourceInfo", null);
  }

  // ---- Maintenance mode ----------------------------------------------------

  @Test
  void filter_allows_get_request_during_maintenance_mode() throws Exception {
    settingsProvider.setMaintenanceMode(true);
    RecordingCtx ctx = new RecordingCtx("GET", "/v1/tracks");

    filter.filter(ctx);

    assertNull(ctx.abortedWith, "GET should not be blocked even during maintenance");
  }

  @Test
  void filter_blocks_post_during_maintenance_mode() throws Exception {
    settingsProvider.setMaintenanceMode(true);
    RecordingCtx ctx = new RecordingCtx("POST", "/v1/tracks");

    filter.filter(ctx);

    assertAborted(ctx, 503, "MAINTENANCE");
  }

  @Test
  void filter_blocks_put_during_maintenance_mode() throws Exception {
    settingsProvider.setMaintenanceMode(true);
    RecordingCtx ctx = new RecordingCtx("PUT", "/v1/tracks/1");

    filter.filter(ctx);

    assertAborted(ctx, 503, "MAINTENANCE");
  }

  @Test
  void filter_blocks_delete_during_maintenance_mode() throws Exception {
    settingsProvider.setMaintenanceMode(true);
    RecordingCtx ctx = new RecordingCtx("DELETE", "/v1/tracks/1");

    filter.filter(ctx);

    assertAborted(ctx, 503, "MAINTENANCE");
  }

  @Test
  void filter_blocks_patch_during_maintenance_mode() throws Exception {
    settingsProvider.setMaintenanceMode(true);
    RecordingCtx ctx = new RecordingCtx("PATCH", "/v1/tracks/1");

    filter.filter(ctx);

    assertAborted(ctx, 503, "MAINTENANCE");
  }

  @Test
  void filter_allows_admin_post_during_maintenance_mode() throws Exception {
    settingsProvider.setMaintenanceMode(true);
    RecordingCtx ctx = new RecordingCtx("POST", "/v1/admin/settings");

    filter.filter(ctx);

    assertNull(ctx.abortedWith, "Admin paths must bypass maintenance block");
  }

  @Test
  void filter_allows_post_when_maintenance_mode_off() throws Exception {
    RecordingCtx ctx = new RecordingCtx("POST", "/v1/tracks");

    filter.filter(ctx);

    assertNull(ctx.abortedWith, "POST should pass when maintenance mode is off");
  }

  // ---- Feature flag (resourceInfo = null → resolveFeatureAnnotation returns null) ------

  @Test
  void filter_passes_when_no_feature_annotation_and_maintenance_off() throws Exception {
    RecordingCtx ctx = new RecordingCtx("GET", "/v1/tracks");

    filter.filter(ctx);

    assertNull(ctx.abortedWith, "No annotation + no maintenance → must pass through");
  }

  // ---- Helpers -------------------------------------------------------------

  private static void assertAborted(RecordingCtx ctx, int expectedStatus, String expectedCode) {
    if (ctx.abortedWith == null) {
      throw new AssertionError("Expected request to be aborted but it was not");
    }
    assertEquals(expectedStatus, ctx.abortedWith.getStatus());
    ErrorEnvelope body = (ErrorEnvelope) ctx.abortedWith.getEntity();
    assertEquals(expectedCode, body.error().code());
  }

  private static void inject(Object target, String fieldName, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(fieldName);
    f.setAccessible(true);
    f.set(target, value);
  }

  /**
   * Minimal {@link ContainerRequestContext} that records the aborted response and answers only the
   * two questions used by {@link PlatformEnforcementFilter}: HTTP method and URI path.
   */
  private static final class RecordingCtx implements ContainerRequestContext {

    Response abortedWith;
    private final String httpMethod;
    private final String uriPath;

    RecordingCtx(String httpMethod, String uriPath) {
      this.httpMethod = httpMethod;
      this.uriPath = uriPath;
    }

    @Override
    public String getMethod() {
      return httpMethod;
    }

    @Override
    public void abortWith(Response response) {
      this.abortedWith = response;
    }

    @Override
    public UriInfo getUriInfo() {
      return new MinimalUriInfo(uriPath);
    }

    @Override
    public Object getProperty(String name) { return null; }

    @Override
    public Collection<String> getPropertyNames() { return List.of(); }

    @Override
    public void setProperty(String name, Object object) {}

    @Override
    public void removeProperty(String name) {}

    @Override
    public void setRequestUri(URI requestUri) {}

    @Override
    public void setRequestUri(URI baseUri, URI requestUri) {}

    @Override
    public Request getRequest() { return null; }

    @Override
    public void setMethod(String method) {}

    @Override
    public MultivaluedMap<String, String> getHeaders() { return new MultivaluedHashMap<>(); }

    @Override
    public String getHeaderString(String name) { return null; }

    @Override
    public Date getDate() { return null; }

    @Override
    public Locale getLanguage() { return null; }

    @Override
    public int getLength() { return -1; }

    @Override
    public MediaType getMediaType() { return null; }

    @Override
    public List<MediaType> getAcceptableMediaTypes() { return List.of(); }

    @Override
    public List<Locale> getAcceptableLanguages() { return List.of(); }

    @Override
    public Map<String, Cookie> getCookies() { return Map.of(); }

    @Override
    public boolean hasEntity() { return false; }

    @Override
    public InputStream getEntityStream() { return null; }

    @Override
    public void setEntityStream(InputStream input) {}

    @Override
    public SecurityContext getSecurityContext() { return null; }

    @Override
    public void setSecurityContext(SecurityContext context) {}
  }

  /** Minimal {@link UriInfo} that only exposes the path. */
  private static final class MinimalUriInfo implements UriInfo {

    private final String path;

    MinimalUriInfo(String path) {
      this.path = path;
    }

    @Override
    public String getPath() { return path; }

    @Override
    public String getPath(boolean decode) { return path; }

    @Override
    public List<PathSegment> getPathSegments() { return List.of(); }

    @Override
    public List<PathSegment> getPathSegments(boolean decode) { return List.of(); }

    @Override
    public URI getRequestUri() { return URI.create("http://localhost:8081" + path); }

    @Override
    public UriBuilder getRequestUriBuilder() { return null; }

    @Override
    public URI getAbsolutePath() { return URI.create("http://localhost:8081" + path); }

    @Override
    public UriBuilder getAbsolutePathBuilder() { return null; }

    @Override
    public URI getBaseUri() { return URI.create("http://localhost:8081/"); }

    @Override
    public UriBuilder getBaseUriBuilder() { return null; }

    @Override
    public List<String> getMatchedURIs() { return List.of(); }

    @Override
    public List<String> getMatchedURIs(boolean decode) { return List.of(); }

    @Override
    public List<Object> getMatchedResources() { return List.of(); }

    @Override
    public URI resolve(URI uri) { return uri; }

    @Override
    public URI relativize(URI uri) { return uri; }

    @Override
    public MultivaluedMap<String, String> getPathParameters() { return new MultivaluedHashMap<>(); }

    @Override
    public MultivaluedMap<String, String> getPathParameters(boolean decode) {
      return new MultivaluedHashMap<>();
    }

    @Override
    public MultivaluedMap<String, String> getQueryParameters() { return new MultivaluedHashMap<>(); }

    @Override
    public MultivaluedMap<String, String> getQueryParameters(boolean decode) {
      return new MultivaluedHashMap<>();
    }
  }
}
