package org.shakvilla.beatzmedia.platform.fakes;

import java.util.concurrent.atomic.AtomicLong;

import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Deterministic test fake for the {@link IdGenerator} output port. Generates sequential,
 * readable IDs. Testing-strategy §2.
 */
public class FakeIds implements IdGenerator {

  private final String prefix;
  private final AtomicLong counter = new AtomicLong(1);
  private final int year;

  private FakeIds(String prefix, int year) {
    this.prefix = prefix;
    this.year = year;
  }

  public static FakeIds sequential(String prefix) {
    return new FakeIds(prefix, 2026);
  }

  public static FakeIds sequential(String prefix, int year) {
    return new FakeIds(prefix, year);
  }

  @Override
  public String newId() {
    return prefix + "-" + counter.getAndIncrement();
  }

  @Override
  public String newOrderRef(int year) {
    return String.format("BZ-%04d-%05d", year, counter.getAndIncrement());
  }

  /** Return the next ID without incrementing (peek). */
  public String peek() {
    return prefix + "-" + counter.get();
  }
}
