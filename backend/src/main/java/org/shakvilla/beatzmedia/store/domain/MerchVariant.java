package org.shakvilla.beatzmedia.store.domain;

import java.util.List;

/**
 * A configurable attribute on a {@code MERCH} {@link StoreItem} (size, colour, …). Value object —
 * no identity of its own outside the owning aggregate. Store ADD §3.
 */
public record MerchVariant(String label, List<String> options) {

  public MerchVariant {
    if (label == null || label.isBlank()) {
      throw new IllegalArgumentException("label must not be blank");
    }
    options = options == null ? List.of() : List.copyOf(options);
  }
}
