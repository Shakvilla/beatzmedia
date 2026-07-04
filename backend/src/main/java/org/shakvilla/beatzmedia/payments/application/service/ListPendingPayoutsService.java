package org.shakvilla.beatzmedia.payments.application.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.payments.application.port.in.ListPendingPayouts;
import org.shakvilla.beatzmedia.payments.application.port.in.MoneyView;
import org.shakvilla.beatzmedia.payments.application.port.in.PendingPayoutView;
import org.shakvilla.beatzmedia.payments.application.port.out.KycProvider;
import org.shakvilla.beatzmedia.payments.application.port.out.PayoutRepository;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethod;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalRequest;

/**
 * Read service for {@link ListPendingPayouts} (API-CONTRACT §Finance). Projects payable withdrawals
 * onto the frontend {@code PendingPayout} shape, tagging each {@code ready} or {@code kyc_pending}
 * from {@link KycProvider} so the admin sees which are KYC-blocked. Read-only; no money moves here.
 */
@ApplicationScoped
public class ListPendingPayoutsService implements ListPendingPayouts {

  private final PayoutRepository payouts;
  private final KycProvider kyc;

  @Inject
  public ListPendingPayoutsService(PayoutRepository payouts, KycProvider kyc) {
    this.payouts = payouts;
    this.kyc = kyc;
  }

  @Override
  @Transactional
  public List<PendingPayoutView> list() {
    List<WithdrawalRequest> payable = payouts.findPayableWithdrawals();
    return payable.stream()
        .map(
            w -> {
              boolean verified = kyc.statusOf(w.getAccountId()).isVerified();
              String status = verified ? "ready" : "kyc_pending";
              String method =
                  payouts
                      .findMethod(w.getAccountId(), w.getMethodId())
                      .map(ListPendingPayoutsService::methodLabel)
                      .orElse("—");
              return new PendingPayoutView(
                  w.getId().value(),
                  w.getAccountId().value(),
                  MoneyView.of(w.getAmount()),
                  method,
                  status);
            })
        .toList();
  }

  private static String methodLabel(PayoutMethod m) {
    String kindLabel = m.getKind().name().equals("bank") ? "Bank" : "MoMo";
    return kindLabel + " · " + m.getLabel();
  }
}
