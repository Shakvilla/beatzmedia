package org.shakvilla.beatzmedia.audit.application.interceptor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.interceptor.InterceptorBinding;

import org.shakvilla.beatzmedia.audit.domain.AuditType;

/**
 * CDI interceptor binding that marks a use-case method as audited (INV-10 / LLFR-AUDIT-01.1).
 * When placed on an application-layer method, {@link AuditInterceptor} writes exactly one
 * {@link org.shakvilla.beatzmedia.audit.domain.AuditEntry} after the method succeeds (inside the
 * same transaction). No row is written if the method throws.
 *
 * <p>Usage:
 *
 * <pre>
 * &#064;Audited(type = AuditType.USER, action = "Suspended user")
 * public void suspend(AccountId actor, String userId, String reason) { ... }
 * </pre>
 *
 * <p>The interceptor reads the actor from the active CDI {@link JsonWebToken} so the annotated
 * method does not need to pass it explicitly. The {@code target} is derived from the first
 * {@link String} argument whose position is annotated with {@link AuditTarget}, or falls back to
 * the first method argument's {@code toString()} value if no annotation is present. Services that
 * already call {@link org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter#append}
 * directly (e.g. WU-IDN-4 identity services) are NOT annotated with {@code @Audited} — they
 * continue to write audit entries themselves to avoid double-writes. Audit ADD §4.2 / §8.1.
 */
@InterceptorBinding
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Audited {

  /** High-level category for this audit event. */
  AuditType type();

  /**
   * Human-readable action label stored in {@code AuditEntry.action}, e.g. {@code "Suspended
   * user"}.
   */
  String action();
}
