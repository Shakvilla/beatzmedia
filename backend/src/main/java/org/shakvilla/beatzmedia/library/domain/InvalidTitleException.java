package org.shakvilla.beatzmedia.library.domain;

/** Thrown when a playlist title violates INV-LIB-3 (must be 1-100 chars after trim). */
public class InvalidTitleException extends RuntimeException {

  public InvalidTitleException(String message) {
    super(message);
  }
}
