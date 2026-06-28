package org.shakvilla.beatzmedia.audit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.application.interceptor.AuditInterceptor;
import org.shakvilla.beatzmedia.audit.application.interceptor.AuditTarget;
import org.shakvilla.beatzmedia.audit.application.interceptor.Audited;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for {@link AuditInterceptor}. Exercises the interceptor logic directly by calling
 * it with a mock {@link jakarta.interceptor.InvocationContext}. Covers INV-10 acceptance criteria:
 * exactly one entry on success, zero on exception. Testing-strategy §2.
 */
@Tag("unit")
class AuditInterceptorTest {

  private static final Instant NOW = Instant.parse("2026-06-27T10:00:00Z");

  private FakeAuditWriter auditWriter;
  private FakeIds ids;
  private FakeClock clock;

  @BeforeEach
  void setUp() {
    auditWriter = new FakeAuditWriter();
    ids = FakeIds.sequential("audit");
    clock = FakeClock.at(NOW);
  }

  // ---- Tests using FakeInvocationContext ----

  @Test
  void intercept_writes_exactly_one_entry_on_success() throws Exception {
    AuditInterceptor interceptor = new AuditInterceptor(auditWriter, ids, clock, null);
    FakeInvocationContext ctx = FakeInvocationContext.forMethod(
        AuditedService.class.getMethod("doSomething", String.class),
        new Object[]{"target-id"},
        false);

    interceptor.intercept(ctx);

    assertEquals(1, auditWriter.size(), "Exactly one audit entry must be written on success");
  }

  @Test
  void intercept_writes_correct_type_and_action() throws Exception {
    AuditInterceptor interceptor = new AuditInterceptor(auditWriter, ids, clock, null);
    FakeInvocationContext ctx = FakeInvocationContext.forMethod(
        AuditedService.class.getMethod("doSomething", String.class),
        new Object[]{"target-id"},
        false);

    interceptor.intercept(ctx);

    AuditEntry entry = auditWriter.all().get(0);
    assertEquals(AuditType.USER, entry.getType());
    assertEquals("Did something", entry.getAction());
  }

  @Test
  void intercept_uses_audit_target_annotation_for_target() throws Exception {
    AuditInterceptor interceptor = new AuditInterceptor(auditWriter, ids, clock, null);
    FakeInvocationContext ctx = FakeInvocationContext.forMethod(
        AuditedService.class.getMethod("withTarget", String.class, String.class),
        new Object[]{"actor-id", "resource-123"},
        false);

    interceptor.intercept(ctx);

    AuditEntry entry = auditWriter.all().get(0);
    // @AuditTarget is on the second parameter (index 1)
    assertEquals("resource-123", entry.getTargetId());
  }

  @Test
  void intercept_uses_unknown_target_when_no_audit_target_annotation() throws Exception {
    // Security: without @AuditTarget the interceptor must NOT serialize an arbitrary argument
    // (which could leak payload PII into target_id); it records "unknown" instead.
    AuditInterceptor interceptor = new AuditInterceptor(auditWriter, ids, clock, null);
    FakeInvocationContext ctx = FakeInvocationContext.forMethod(
        AuditedService.class.getMethod("doSomething", String.class),
        new Object[]{"sensitive@example.com"},
        false);

    interceptor.intercept(ctx);

    AuditEntry entry = auditWriter.all().get(0);
    assertEquals("unknown", entry.getTargetId());
  }

  @Test
  void intercept_writes_zero_entries_on_exception() throws Exception {
    AuditInterceptor interceptor = new AuditInterceptor(auditWriter, ids, clock, null);
    FakeInvocationContext ctx = FakeInvocationContext.forMethod(
        AuditedService.class.getMethod("doSomething", String.class),
        new Object[]{"target-id"},
        true /* throw */);

    assertThrows(RuntimeException.class, () -> interceptor.intercept(ctx));
    assertEquals(0, auditWriter.size(), "No audit entry must be written when method throws");
  }

  @Test
  void intercept_sets_occurred_at_from_clock() throws Exception {
    AuditInterceptor interceptor = new AuditInterceptor(auditWriter, ids, clock, null);
    FakeInvocationContext ctx = FakeInvocationContext.forMethod(
        AuditedService.class.getMethod("doSomething", String.class),
        new Object[]{"t"},
        false);

    interceptor.intercept(ctx);

    AuditEntry entry = auditWriter.all().get(0);
    assertEquals(NOW, entry.getOccurredAt());
  }

  @Test
  void intercept_sets_actor_unknown_when_jwt_is_null() throws Exception {
    AuditInterceptor interceptor = new AuditInterceptor(auditWriter, ids, clock, null);
    FakeInvocationContext ctx = FakeInvocationContext.forMethod(
        AuditedService.class.getMethod("doSomething", String.class),
        new Object[]{"t"},
        false);

    interceptor.intercept(ctx);

    AuditEntry entry = auditWriter.all().get(0);
    assertNotNull(entry.getActor());
  }

  // ---- Helpers ----

  /**
   * A service class with methods annotated {@code @Audited} so the interceptor can read the
   * annotation via reflection.
   */
  static class AuditedService {

    @Audited(type = AuditType.USER, action = "Did something")
    public String doSomething(String targetId) {
      return "ok";
    }

    @Audited(type = AuditType.SETTINGS, action = "Updated target")
    public String withTarget(String actor, @AuditTarget String resourceId) {
      return "ok";
    }
  }

  /**
   * Minimal InvocationContext fake that supplies the target method and optional exception throwing.
   */
  static class FakeInvocationContext
      implements jakarta.interceptor.InvocationContext {

    private final java.lang.reflect.Method method;
    private final Object[] params;
    private final boolean shouldThrow;

    private FakeInvocationContext(
        java.lang.reflect.Method method, Object[] params, boolean shouldThrow) {
      this.method = method;
      this.params = params;
      this.shouldThrow = shouldThrow;
    }

    static FakeInvocationContext forMethod(
        java.lang.reflect.Method method, Object[] params, boolean shouldThrow) {
      return new FakeInvocationContext(method, params, shouldThrow);
    }

    @Override
    public Object getTarget() {
      return null;
    }

    @Override
    public Object getTimer() {
      return null;
    }

    @Override
    public java.lang.reflect.Method getMethod() {
      return method;
    }

    @Override
    public java.lang.reflect.Constructor<?> getConstructor() {
      return null;
    }

    @Override
    public Object[] getParameters() {
      return params;
    }

    @Override
    public void setParameters(Object[] parameters) {
      // no-op
    }

    @Override
    public java.util.Map<String, Object> getContextData() {
      return java.util.Collections.emptyMap();
    }

    @Override
    public Object proceed() throws Exception {
      if (shouldThrow) {
        throw new RuntimeException("Simulated exception");
      }
      return "result";
    }
  }
}
