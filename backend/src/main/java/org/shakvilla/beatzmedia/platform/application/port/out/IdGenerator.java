package org.shakvilla.beatzmedia.platform.application.port.out;

/**
 * Output port for generating unique identifiers. Injected everywhere; tested via {@code FakeIds}.
 * ArchUnit enforces no direct calls to {@code UUID.randomUUID()} in core code. Conventions §3.
 */
public interface IdGenerator {

  /** Generate a new sortable unique ID (UUIDv7 / ULID). */
  String newId();

  /**
   * Generate a human-readable order reference in the format {@code BZ-YYYY-NNNNN}. Conventions §3.
   *
   * @param year 4-digit year (e.g. 2026)
   */
  String newOrderRef(int year);
}
