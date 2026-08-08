package org.shakvilla.beatzmedia.platform.domain;

/**
 * Thrown when a request names a taxonomy kind that does not exist. Maps to HTTP 422.
 *
 * <p>Deliberately an error rather than an empty result: {@code ?kind=genres} (a plausible typo for
 * {@code genre}) returning {@code []} is indistinguishable, to an operator, from a taxonomy that has
 * simply had all its terms deleted.
 */
public class InvalidTaxonomyKindException extends ValidationException {

  public InvalidTaxonomyKindException(String message) {
    super(message, "kind");
  }
}
