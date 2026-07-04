package org.shakvilla.beatzmedia.payments.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.payments.application.port.out.KycProvider;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.KycStatus;

/**
 * JPA-backed {@link KycProvider} reading the payments-owned {@code kyc_record} table (V704, INV-8).
 * The ADD models KYC as resolving to the identity module; identity has no KYC surface yet, so the
 * authoritative record lives here behind this port. A future identity KYC WU can swap the
 * implementation without touching the withdrawal/payout services. A creator with no record is {@link
 * KycStatus#NONE} (not verified) — so the default is fail-closed (INV-8).
 */
@ApplicationScoped
public class JpaKycProvider implements KycProvider {

  private final EntityManager em;

  @Inject
  public JpaKycProvider(EntityManager em) {
    this.em = em;
  }

  @Override
  public KycStatus statusOf(AccountId creator) {
    KycRecordEntity entity = em.find(KycRecordEntity.class, creator.value());
    if (entity == null) {
      return KycStatus.NONE;
    }
    return KycStatus.fromWire(entity.status);
  }
}
