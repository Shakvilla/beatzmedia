package org.shakvilla.beatzmedia.audit.application.interceptor;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * CDI interceptor for {@link Audited}. Writes exactly one {@link AuditEntry} after the intercepted
 * method returns successfully (inside the same transaction). No row is written if the method throws
 * an exception. INV-10 / audit ADD §4.2 / §8.1.
 *
 * <p>Actor is read from the MicroProfile JWT {@link JsonWebToken} (CDI request-scoped). The target
 * string is resolved from the first method parameter annotated with {@link AuditTarget}, falling
 * back to the first parameter's {@code toString()} if none is found.
 *
 * <p>The interceptor is intentionally NOT applied to WU-IDN-4 identity services that call
 * {@link AuditWriter#append} directly — those services already write their own audit rows. This
 * avoids double-writes while providing declarative auditing for future use cases. See audit ADD ADR
 * (analytics-audit-platform.md §2).
 */
@Audited(type = org.shakvilla.beatzmedia.audit.domain.AuditType.USER, action = "")
@Interceptor
@Priority(Interceptor.Priority.APPLICATION + 10)
public class AuditInterceptor {

  private final AuditWriter auditWriter;
  private final IdGenerator idGenerator;
  private final Clock clock;
  private final JsonWebToken jwt;

  @Inject
  public AuditInterceptor(
      AuditWriter auditWriter, IdGenerator idGenerator, Clock clock, JsonWebToken jwt) {
    this.auditWriter = auditWriter;
    this.idGenerator = idGenerator;
    this.clock = clock;
    this.jwt = jwt;
  }

  @AroundInvoke
  public Object intercept(InvocationContext ctx) throws Exception {
    // Proceed first — only write audit row on success (no row on exception)
    Object result = ctx.proceed();

    // Resolve @Audited metadata from the method (method-level takes precedence over type-level)
    Audited audited = resolveAudited(ctx.getMethod());
    if (audited == null) {
      return result;
    }

    String actorId = jwt != null ? jwt.getSubject() : "unknown";
    String actorName = jwt != null ? jwt.getName() : null;
    String target = resolveTarget(ctx.getMethod(), ctx.getParameters());

    auditWriter.append(new AuditEntry(
        idGenerator.newId(),
        actorId,
        actorName,
        audited.action(),
        resolveTargetType(ctx.getMethod()),
        target,
        audited.type(),
        null,
        clock.now()));

    return result;
  }

  /**
   * Resolves the {@link Audited} annotation, preferring method-level over type-level.
   */
  private Audited resolveAudited(Method method) {
    Audited methodLevel = method.getAnnotation(Audited.class);
    if (methodLevel != null) {
      return methodLevel;
    }
    return method.getDeclaringClass().getAnnotation(Audited.class);
  }

  /**
   * Resolves the audit target string from the parameter annotated with {@link AuditTarget}. When no
   * parameter is annotated we deliberately return {@code "unknown"} rather than serializing an
   * arbitrary argument: blindly persisting {@code params[0].toString()} could leak request/command
   * payload (e.g. emails) into {@code audit_entry.target_id}. Annotate the target parameter with
   * {@code @AuditTarget} to record a meaningful target id.
   */
  private String resolveTarget(Method method, Object[] params) {
    if (params == null || params.length == 0) {
      return "unknown";
    }
    Parameter[] methodParams = method.getParameters();
    for (int i = 0; i < methodParams.length; i++) {
      if (methodParams[i].isAnnotationPresent(AuditTarget.class)) {
        return params[i] != null ? params[i].toString() : "null";
      }
    }
    return "unknown";
  }

  /**
   * Returns the simple name of the declaring class as the target type.
   */
  private String resolveTargetType(Method method) {
    return method.getDeclaringClass().getSimpleName();
  }
}
