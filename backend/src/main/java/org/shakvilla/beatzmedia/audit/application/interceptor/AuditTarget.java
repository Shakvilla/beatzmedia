package org.shakvilla.beatzmedia.audit.application.interceptor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Parameter-level annotation that marks a method argument as the audit target for the
 * {@link AuditInterceptor}. If present, the interceptor uses this parameter's {@code toString()}
 * as the {@code target} field of the {@link org.shakvilla.beatzmedia.audit.domain.AuditEntry}.
 *
 * <p>Usage:
 *
 * <pre>
 * &#064;Audited(type = AuditType.USER, action = "Suspended user")
 * public void suspend(AccountId actor, &#064;AuditTarget String userId, String reason) { ... }
 * </pre>
 *
 * Audit ADD §4.2.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditTarget {
}
