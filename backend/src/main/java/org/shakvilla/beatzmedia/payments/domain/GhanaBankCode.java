package org.shakvilla.beatzmedia.payments.domain;

/**
 * The bank codes Redde accepts for a bank cash-out ({@code POST /v1/cashout} with {@code
 * paymentoption=BANK}), per the Redde docs bank-code table (WU-PAY-7). A creator's bank {@link
 * PayoutMethod} stores one of these tokens in {@code bank_code}; it is validated on add so an invalid
 * code is a clean {@code 422} rather than a rail rejection at disbursement time. Framework-free.
 *
 * <p>The enum constant name IS the wire token Redde expects (e.g. {@code GCB}); {@link #displayName}
 * is for human-facing surfaces only and is never sent to the rail.
 */
public enum GhanaBankCode {
  GCB("GCB Bank"),
  CAL("CalBank"),
  EBL("Ecobank Ghana"),
  ABB("Absa Bank Ghana"),
  UBA("United Bank for Africa"),
  ADB("Agricultural Development Bank"),
  APX("Apex Bank"),
  BOA("Bank of Africa"),
  BBG("Bank of Baroda Ghana"),
  FBN("First Bank of Nigeria"),
  FBL("Fidelity Bank Ghana"),
  FAB("First Atlantic Bank"),
  FNB("First National Bank Ghana"),
  GTB("Guaranty Trust Bank Ghana"),
  NIB("National Investment Bank"),
  OMN("OmniBSIC Bank"),
  PBL("Prudential Bank"),
  RBL("Republic Bank Ghana"),
  SGG("Societe Generale Ghana"),
  CBG("Consolidated Bank Ghana"),
  SBL("Stanbic Bank Ghana"),
  SCB("Standard Chartered Bank Ghana"),
  ZBL("Zenith Bank Ghana");

  private final String displayName;

  GhanaBankCode(String displayName) {
    this.displayName = displayName;
  }

  public String displayName() {
    return displayName;
  }

  /** True iff {@code code} is a known Redde bank-code token (case-sensitive, as Redde expects). */
  public static boolean isValid(String code) {
    if (code == null) {
      return false;
    }
    for (GhanaBankCode c : values()) {
      if (c.name().equals(code)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Resolve a wire token to the enum.
   *
   * @throws IllegalArgumentException if {@code code} is not a known bank code (the application service
   *     translates this to a mapped {@code 422})
   */
  public static GhanaBankCode of(String code) {
    if (!isValid(code)) {
      throw new IllegalArgumentException("unknown Ghana bank code: " + code);
    }
    return valueOf(code);
  }
}
