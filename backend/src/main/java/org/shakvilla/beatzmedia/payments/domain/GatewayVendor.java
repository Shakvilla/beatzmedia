package org.shakvilla.beatzmedia.payments.domain;

/** Gateway recorded on a charge or payout when that operation is created. */
public enum GatewayVendor {
  SANDBOX("sandbox"),
  REDDE("redde"),
  DISABLED("disabled"),
  UNKNOWN("unknown");

  private final String wireValue;

  GatewayVendor(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }

  public static GatewayVendor fromWireValue(String value) {
    for (GatewayVendor vendor : values()) {
      if (vendor.wireValue.equals(value)) {
        return vendor;
      }
    }
    throw new IllegalArgumentException("unsupported payment gateway: " + value);
  }
}
