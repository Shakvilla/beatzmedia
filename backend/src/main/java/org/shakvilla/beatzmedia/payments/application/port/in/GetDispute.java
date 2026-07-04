package org.shakvilla.beatzmedia.payments.application.port.in;

import org.shakvilla.beatzmedia.payments.domain.DisputeId;

/**
 * Input port: admin reads a dispute detail + timeline (LLFR-PAYMENTS-04.1). Auth: finance/super-admin
 * (enforced at the REST boundary). No money movement.
 */
public interface GetDispute {

  /**
   * @throws org.shakvilla.beatzmedia.payments.domain.DisputeNotFoundException if the id is unknown
   */
  DisputeView get(DisputeId id);
}
