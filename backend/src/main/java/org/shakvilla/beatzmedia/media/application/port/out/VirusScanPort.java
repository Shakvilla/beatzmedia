package org.shakvilla.beatzmedia.media.application.port.out;

import org.shakvilla.beatzmedia.media.domain.ObjectKey;

/**
 * Output port for virus/malware scanning. v1 ships a stub adapter that always returns clean.
 * The FILE_REJECTED code path is wired and exercised in tests. ADD §9.
 */
public interface VirusScanPort {

  /**
   * Scan the object at the given key.
   *
   * @return true if the file is clean; false if it should be rejected
   */
  boolean isClean(ObjectKey key);
}
