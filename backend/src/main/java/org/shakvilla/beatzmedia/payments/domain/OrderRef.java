package org.shakvilla.beatzmedia.payments.domain;

/**
 * The commerce order reference a charge settles against (format {@code BZ-YYYY-NNNNN}, cross-module
 * id only). The payments module never joins to the commerce order table — it stores the reference
 * as an opaque string. Payments ADD §2 / §3.
 */
public record OrderRef(String value) {

  public OrderRef {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("orderRef must not be blank");
    }
  }
}
