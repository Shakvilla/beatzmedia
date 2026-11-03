package org.shakvilla.beatzmedia.platform.application.port.out;

import org.shakvilla.beatzmedia.platform.domain.ProviderCapability;
import org.shakvilla.beatzmedia.platform.domain.ProviderCode;

/** Adapter supplied readiness check used before a provider may be activated. */
public interface ProviderReadinessProbe {
  Readiness check(ProviderCapability capability, ProviderCode provider);

  record Readiness(boolean ready, String reason) {
    public static Readiness available() {
      return new Readiness(true, "ready");
    }

    public static Readiness unavailable(String reason) {
      return new Readiness(false, reason);
    }
  }
}
