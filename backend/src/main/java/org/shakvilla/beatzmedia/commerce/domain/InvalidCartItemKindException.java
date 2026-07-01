package org.shakvilla.beatzmedia.commerce.domain;

import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/** Thrown when a cart item {@code kind} is not one of the recognized enum values. Maps to 422. */
public class InvalidCartItemKindException extends ValidationException {

  public InvalidCartItemKindException(String kind) {
    super("Unknown cart item kind: " + kind, "kind");
  }
}
