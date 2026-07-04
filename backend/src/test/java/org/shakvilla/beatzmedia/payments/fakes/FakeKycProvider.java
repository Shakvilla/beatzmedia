package org.shakvilla.beatzmedia.payments.fakes;

import java.util.HashMap;
import java.util.Map;

import org.shakvilla.beatzmedia.payments.application.port.out.KycProvider;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.KycStatus;

/**
 * In-memory {@link KycProvider} fake. Default is {@link KycStatus#NONE} (fail-closed, INV-8); tests
 * mark specific creators verified. Testing-strategy §2.
 */
public class FakeKycProvider implements KycProvider {

  private final Map<String, KycStatus> statuses = new HashMap<>();

  @Override
  public KycStatus statusOf(AccountId creator) {
    return statuses.getOrDefault(creator.value(), KycStatus.NONE);
  }

  /** Mark a creator KYC-verified (test helper). */
  public FakeKycProvider verify(AccountId creator) {
    statuses.put(creator.value(), KycStatus.VERIFIED);
    return this;
  }

  /** Set an explicit status for a creator (test helper). */
  public FakeKycProvider set(AccountId creator, KycStatus status) {
    statuses.put(creator.value(), status);
    return this;
  }
}
