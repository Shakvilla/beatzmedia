package org.shakvilla.beatzmedia.admin.application.port.in;

import java.time.Instant;
import java.util.List;

import org.shakvilla.beatzmedia.platform.application.port.in.ManageProviderSelections.ProviderSelectionView;

/** Super-admin use case for reading and auditing provider selection changes. */
public interface ManageProviderConfig {
  List<ProviderSelectionView> list();

  ProviderSelectionView change(
      String actorId, String capability, String provider, long expectedVersion);

  ResendConfigView resendConfig();

  ResendConfigView saveResendConfig(
      String actorId, String apiKey, String from, long expectedVersion);

  FrogConfigView frogConfig();

  FrogConfigView saveFrogConfig(
      String actorId, String apiKey, String username, String senderId, long expectedVersion);

  record ResendConfigView(
      boolean configured,
      String from,
      String source,
      long version,
      Instant updatedAt,
      String updatedBy,
      boolean encryptionReady) {}

  record FrogConfigView(
      boolean configured,
      String senderId,
      String source,
      long version,
      Instant updatedAt,
      String updatedBy,
      boolean encryptionReady) {}
}
