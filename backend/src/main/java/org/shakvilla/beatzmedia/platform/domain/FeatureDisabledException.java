package org.shakvilla.beatzmedia.platform.domain;

/** Thrown when a feature is disabled via feature flags. Maps to HTTP 403 FEATURE_DISABLED. */
public class FeatureDisabledException extends DomainException {

  public FeatureDisabledException(String featureKey) {
    super(ErrorCode.FEATURE_DISABLED, "Feature is currently disabled: " + featureKey);
  }
}
