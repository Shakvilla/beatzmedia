package org.shakvilla.beatzmedia.notifications.fakes;

import java.util.ArrayList;
import java.util.List;

import org.shakvilla.beatzmedia.notifications.application.port.in.NotifyCommand;
import org.shakvilla.beatzmedia.notifications.application.port.in.NotifyUseCase;
import org.shakvilla.beatzmedia.notifications.domain.NotificationId;

/** Capturing {@link NotifyUseCase} fake — records the commands an event observer forwards. */
public class FakeNotifyUseCase implements NotifyUseCase {

  public final List<NotifyCommand> commands = new ArrayList<>();

  @Override
  public NotificationId notify(NotifyCommand command) {
    commands.add(command);
    return new NotificationId("notif-" + commands.size());
  }
}
