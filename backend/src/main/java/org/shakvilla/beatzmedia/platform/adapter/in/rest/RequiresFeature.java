package org.shakvilla.beatzmedia.platform.adapter.in.rest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.shakvilla.beatzmedia.platform.domain.FeatureKey;

/**
 * Marks a JAX-RS resource method or class as requiring a specific feature flag to be enabled. If
 * the feature is disabled, the enforcement filter returns 403 FEATURE_DISABLED. ADD §5.2.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresFeature {

  /** The feature key that must be enabled for this endpoint to be accessible. */
  FeatureKey value();
}
