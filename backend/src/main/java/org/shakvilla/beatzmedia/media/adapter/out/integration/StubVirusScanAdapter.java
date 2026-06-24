package org.shakvilla.beatzmedia.media.adapter.out.integration;

import jakarta.enterprise.context.ApplicationScoped;

import org.shakvilla.beatzmedia.media.application.port.out.VirusScanPort;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;

/**
 * Stub virus-scan adapter. v1 always returns clean. The FILE_REJECTED code path is wired and
 * tested via the fake. Replace with a real ClamAV adapter when needed. ADD §9.
 */
@ApplicationScoped
public class StubVirusScanAdapter implements VirusScanPort {

  @Override
  public boolean isClean(ObjectKey key) {
    return true;
  }
}
