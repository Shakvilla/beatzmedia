package org.shakvilla.beatzmedia.platform.domain;

import java.util.Arrays;
import java.util.Locale;

/** Closed provider code set. A code is valid for exactly one capability. */
public enum ProviderCode {
  MINIO(ProviderCapability.STORAGE),
  MEGA_S4(ProviderCapability.STORAGE),
  SMTP(ProviderCapability.EMAIL),
  RESEND(ProviderCapability.EMAIL),
  EMAIL_DISABLED(ProviderCapability.EMAIL, "disabled"),
  CAPTURE(ProviderCapability.SMS),
  FROG(ProviderCapability.SMS),
  SMS_DISABLED(ProviderCapability.SMS, "disabled"),
  SANDBOX(ProviderCapability.PAYMENT),
  REDDE(ProviderCapability.PAYMENT),
  PAYMENT_DISABLED(ProviderCapability.PAYMENT, "disabled");

  private final ProviderCapability capability;
  private final String wireValue;

  ProviderCode(ProviderCapability capability) {
    this(capability, null);
  }

  ProviderCode(ProviderCapability capability, String wireValue) {
    this.capability = capability;
    this.wireValue = wireValue == null ? name().toLowerCase(Locale.ROOT) : wireValue;
  }

  public ProviderCapability capability() {
    return capability;
  }

  public String wireValue() {
    return wireValue;
  }

  public static ProviderCode fromWireValue(ProviderCapability capability, String value) {
    if (value == null || value.isBlank()) {
      throw new ValidationException("provider is required", "provider");
    }
    return Arrays.stream(values())
        .filter(code -> code.capability == capability && code.wireValue.equalsIgnoreCase(value.trim()))
        .findFirst()
        .orElseThrow(
            () ->
                new ValidationException(
                    "Provider " + value + " does not support " + capability.wireValue(), "provider"));
  }
}
