package org.shakvilla.beatzmedia.platform.domain;

/**
 * Which controlled list a {@link TaxonomyTerm} belongs to.
 *
 * <p>Each of these was previously hardcoded somewhere it could not be edited: {@link #GENRE} was a
 * TypeScript union in the frontend, {@link #PODCAST_CATEGORY} and {@link #EVENT_CATEGORY} were
 * Postgres CHECK constraints, and {@link #BROWSE_CATEGORY} had its own table. They live in one kind
 * space now so a fifth taxonomy costs a constant, not a migration.
 */
public enum TaxonomyKind {
  GENRE("genre"),
  PODCAST_CATEGORY("podcast_category"),
  EVENT_CATEGORY("event_category"),
  BROWSE_CATEGORY("browse_category");

  private final String wireValue;

  TaxonomyKind(String wireValue) {
    this.wireValue = wireValue;
  }

  /** The value stored in {@code taxonomy_term.kind} and used on the wire. */
  public String wireValue() {
    return wireValue;
  }

  /**
   * Parses a snake_case wire value (e.g. {@code "podcast_category"}).
   *
   * @throws InvalidTaxonomyKindException when the value matches no kind — the REST layer maps this
   *     to 422 rather than letting an unknown kind silently return an empty list, which would look
   *     to an operator exactly like a taxonomy with nothing in it.
   */
  public static TaxonomyKind fromWireValue(String value) {
    if (value == null) {
      throw new InvalidTaxonomyKindException("Taxonomy kind must not be null");
    }
    for (TaxonomyKind k : values()) {
      if (k.wireValue.equalsIgnoreCase(value)) {
        return k;
      }
    }
    throw new InvalidTaxonomyKindException("Unknown taxonomy kind: " + value);
  }
}
