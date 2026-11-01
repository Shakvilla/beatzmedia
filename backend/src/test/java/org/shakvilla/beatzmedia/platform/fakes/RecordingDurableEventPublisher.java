package org.shakvilla.beatzmedia.platform.fakes;

import java.util.ArrayList;
import java.util.List;

import org.shakvilla.beatzmedia.platform.application.port.out.DurableEventPublisher;
import org.shakvilla.beatzmedia.platform.domain.DurableEventMessage;

public final class RecordingDurableEventPublisher implements DurableEventPublisher {

  private final List<DurableEventMessage> messages = new ArrayList<>();

  @Override
  public void append(DurableEventMessage message) {
    messages.add(message);
  }

  public List<DurableEventMessage> messages() {
    return List.copyOf(messages);
  }
}
