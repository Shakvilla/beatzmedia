package org.shakvilla.beatzmedia.media.fakes;

import org.shakvilla.beatzmedia.media.application.port.out.VirusScanPort;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;

/** Configurable fake for {@link VirusScanPort}. Default: clean (returns true). */
public class FakeVirusScan implements VirusScanPort {

  private boolean clean = true;

  public void setClean(boolean clean) {
    this.clean = clean;
  }

  @Override
  public boolean isClean(ObjectKey key) {
    return clean;
  }
}
