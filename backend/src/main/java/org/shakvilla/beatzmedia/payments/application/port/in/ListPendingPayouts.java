package org.shakvilla.beatzmedia.payments.application.port.in;

import java.util.List;

/**
 * Input port: list payable withdrawals across creators for the admin finance screen (API-CONTRACT
 * §Finance, LLFR-PAYMENTS-03.3). Each row is tagged {@code ready} or {@code kyc_pending} so the admin
 * sees which are blocked on KYC. Auth: finance / super-admin.
 */
public interface ListPendingPayouts {

  List<PendingPayoutView> list();
}
