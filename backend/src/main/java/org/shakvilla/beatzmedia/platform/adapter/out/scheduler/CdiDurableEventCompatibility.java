package org.shakvilla.beatzmedia.platform.adapter.out.scheduler;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.platform.application.port.in.DurableEventHandler;
import org.shakvilla.beatzmedia.platform.application.port.out.DurableEventCompatibility;

@ApplicationScoped
public class CdiDurableEventCompatibility implements DurableEventCompatibility {

  private final Instance<DurableEventHandler> handlers;

  @Inject
  public CdiDurableEventCompatibility(Instance<DurableEventHandler> handlers) {
    this.handlers = handlers;
  }

  @Override
  public boolean supports(String eventType, int schemaVersion) {
    for (DurableEventHandler handler : handlers) {
      if (handler.eventType().equals(eventType) && handler.schemaVersion() == schemaVersion) {
        return true;
      }
    }
    return false;
  }
}
