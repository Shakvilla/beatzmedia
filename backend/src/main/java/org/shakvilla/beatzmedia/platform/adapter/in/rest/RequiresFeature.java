package org.shakvilla.beatzmedia.platform.adapter.in.rest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.shakvilla.beatzmedia.platform.domain.FeatureKey;

/**
 * Marks a JAX-RS resource method or class as requiring feature flags to be enabled. If any listed
 * feature is disabled, the enforcement filter returns 403 FEATURE_DISABLED. ADD §5.2.
 *
 * <p>A method annotation replaces the class annotation rather than adding to it, so a method that
 * needs its class's feature <em>and</em> another must list both. Tipping on a podcast is the case
 * that made this an array: it belongs to {@code PODCASTS} and {@code TIPPING}, and switching either
 * off must stop it. With a single-valued annotation the method would have silently escaped its
 * class's flag.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresFeature {

  /** Every feature key that must be enabled for this endpoint to be accessible. */
  FeatureKey[] value();
}
