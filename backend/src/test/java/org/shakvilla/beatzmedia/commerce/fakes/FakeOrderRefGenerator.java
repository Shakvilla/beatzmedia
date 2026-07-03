package org.shakvilla.beatzmedia.commerce.fakes;

import java.util.concurrent.atomic.AtomicLong;

import org.shakvilla.beatzmedia.commerce.application.port.out.OrderRefGenerator;

/** Deterministic fake {@link OrderRefGenerator} — a monotonic counter, like the real DB sequence. */
public class FakeOrderRefGenerator implements OrderRefGenerator {

  private final AtomicLong seq = new AtomicLong(0);

  @Override
  public String nextReference(int year) {
    return String.format("BZ-%04d-%05d", year, seq.incrementAndGet());
  }
}
