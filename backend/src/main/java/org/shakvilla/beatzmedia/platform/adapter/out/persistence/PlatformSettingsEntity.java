package org.shakvilla.beatzmedia.platform.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the single-row {@code platform_settings} table. Domain types carry no ORM
 * annotations — mapping is done in the adapter. ADD §7.
 */
@Entity
@Table(name = "platform_settings")
public class PlatformSettingsEntity {

  @Id
  @Column(name = "id")
  private short id;

  @Column(name = "platform_fee_pct", nullable = false)
  private int platformFeePct;

  @Column(name = "creator_share_pct", nullable = false)
  private int creatorSharePct;

  @Column(name = "tip_fee_pct", nullable = false)
  private int tipFeePct;

  @Column(name = "bundle_discount_pct", nullable = false)
  private int bundleDiscountPct;

  @Column(name = "payout_day", nullable = false)
  private String payoutDay;

  @Column(name = "payout_minimum_minor", nullable = false)
  private long payoutMinimumMinor;

  @Column(name = "service_fee_minor", nullable = false)
  private long serviceFeeMinor;

  @Column(name = "default_currency", nullable = false)
  private String defaultCurrency;

  @Column(name = "is_maintenance_mode", nullable = false)
  private boolean maintenanceMode;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  // Default constructor required by JPA
  public PlatformSettingsEntity() {}

  public short getId() {
    return id;
  }

  public void setId(short id) {
    this.id = id;
  }

  public int getPlatformFeePct() {
    return platformFeePct;
  }

  public void setPlatformFeePct(int platformFeePct) {
    this.platformFeePct = platformFeePct;
  }

  public int getCreatorSharePct() {
    return creatorSharePct;
  }

  public void setCreatorSharePct(int creatorSharePct) {
    this.creatorSharePct = creatorSharePct;
  }

  public int getTipFeePct() {
    return tipFeePct;
  }

  public void setTipFeePct(int tipFeePct) {
    this.tipFeePct = tipFeePct;
  }

  public int getBundleDiscountPct() {
    return bundleDiscountPct;
  }

  public void setBundleDiscountPct(int bundleDiscountPct) {
    this.bundleDiscountPct = bundleDiscountPct;
  }

  public String getPayoutDay() {
    return payoutDay;
  }

  public void setPayoutDay(String payoutDay) {
    this.payoutDay = payoutDay;
  }

  public long getPayoutMinimumMinor() {
    return payoutMinimumMinor;
  }

  public void setPayoutMinimumMinor(long payoutMinimumMinor) {
    this.payoutMinimumMinor = payoutMinimumMinor;
  }

  public long getServiceFeeMinor() {
    return serviceFeeMinor;
  }

  public void setServiceFeeMinor(long serviceFeeMinor) {
    this.serviceFeeMinor = serviceFeeMinor;
  }

  public String getDefaultCurrency() {
    return defaultCurrency;
  }

  public void setDefaultCurrency(String defaultCurrency) {
    this.defaultCurrency = defaultCurrency;
  }

  public boolean isMaintenanceMode() {
    return maintenanceMode;
  }

  public void setMaintenanceMode(boolean maintenanceMode) {
    this.maintenanceMode = maintenanceMode;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
