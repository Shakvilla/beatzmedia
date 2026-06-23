package org.shakvilla.beatzmedia.platform.adapter.out.persistence;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.enterprise.context.ApplicationScoped;

import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * UUIDv7-based implementation of {@link IdGenerator}. UUIDv7 is time-ordered (ms precision in the
 * high bits) which gives k-sortable IDs ideal for primary keys. Order references follow the format
 * {@code BZ-YYYY-NNNNN}. Conventions §3 / ADD §4.3.
 *
 * <p>This is the only place in the codebase that generates random IDs — ArchUnit enforces no
 * {@code UUID.randomUUID()} calls in domain/application code.
 */
@ApplicationScoped
public class Uuidv7IdGenerator implements IdGenerator {

  private static final SecureRandom RANDOM = new SecureRandom();
  private final AtomicLong orderRefSeq = new AtomicLong(0);

  @Override
  public String newId() {
    return generateUuidv7();
  }

  @Override
  public String newOrderRef(int year) {
    long seq = orderRefSeq.incrementAndGet() % 100_000L;
    return String.format("BZ-%04d-%05d", year, seq);
  }

  /**
   * Generate a UUID v7 string (RFC 9562). Layout:
   *
   * <pre>
   * [unix_ts_ms: 48 bits][ver: 4 bits][rand_a: 12 bits][var: 2 bits][rand_b: 62 bits]
   * </pre>
   */
  private static String generateUuidv7() {
    long tsMs = Instant.now().toEpochMilli();
    byte[] rand = new byte[10];
    RANDOM.nextBytes(rand);

    long msb = (tsMs << 16) | 0x7000L | ((rand[0] & 0x0FL) << 8) | (rand[1] & 0xFFL);
    long lsb = (0x8000000000000000L)
        | ((rand[2] & 0x3FL) << 56)
        | ((rand[3] & 0xFFL) << 48)
        | ((rand[4] & 0xFFL) << 40)
        | ((rand[5] & 0xFFL) << 32)
        | ((rand[6] & 0xFFL) << 24)
        | ((rand[7] & 0xFFL) << 16)
        | ((rand[8] & 0xFFL) << 8)
        | (rand[9] & 0xFFL);

    HexFormat hex = HexFormat.of();
    String msbHex = hex.toHexDigits(msb);
    String lsbHex = hex.toHexDigits(lsb);
    return msbHex.substring(0, 8)
        + "-" + msbHex.substring(8, 12)
        + "-" + msbHex.substring(12, 16)
        + "-" + lsbHex.substring(0, 4)
        + "-" + lsbHex.substring(4);
  }
}
