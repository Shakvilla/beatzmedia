package org.shakvilla.beatzmedia.payments.adapter.out.integration;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.inject.Qualifier;

/**
 * CDI qualifier distinguishing the two concrete {@code PaymentGateway} implementations (WU-PAY-6).
 * Applying it to {@code SandboxPaymentGateway}/{@code ReddePaymentGateway} strips their {@code
 * @Default} qualifier, so {@code PaymentGatewayRouter} (unqualified) is the sole bean every existing
 * {@code @Inject PaymentGateway} site resolves to; the router selects between the two by
 * {@link org.shakvilla.beatzmedia.platform.domain.FeatureKey#PSP_REDDE}. The {@link #value()} is a
 * binding member, so {@code @PspGateway(SANDBOX)} injects the sandbox bean and {@code
 * @PspGateway(REDDE)} the Redde bean.
 */
@Qualifier
@Retention(RUNTIME)
@Target({TYPE, FIELD, PARAMETER, METHOD})
public @interface PspGateway {

  Vendor value();

  /** Which concrete gateway a bean/injection point refers to. */
  enum Vendor {
    SANDBOX,
    REDDE
  }
}
