package org.shakvilla.beatzmedia.platform.application.port.out;

public interface DurableEventCompatibility {
  boolean supports(String eventType, int schemaVersion);
}
