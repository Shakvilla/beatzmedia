package org.shakvilla.beatzmedia.commerce.adapter.in.rest;

import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

import org.shakvilla.beatzmedia.platform.domain.RateLimitedException;

/**
 * Per-account token-bucket rate limiter for the checkout money path (security-authz §6 / WU-PAY-1
 * carryover). Guards {@code POST /v1/checkout} so a single fan cannot hammer the charge rail: a bucket
 * of {@link #CAPACITY} tokens refills at {@link #REFILL_PER_SEC} tokens/second; an empty bucket
 * throws {@link RateLimitedException} (→ 429 + {@code Retry-After}). In-process/per-instance — adequate
 * for v1; a shared store (Redis) is the multi-instance upgrade path.
 *
 * <p>Deliberately keyed on the authenticated account (not IP) so it cannot be evaded by rotating IPs
 * and cannot punish a shared NAT. The window is generous enough for legitimate retries but blocks
 * abusive bursts.
 */
@ApplicationScoped
public class CheckoutRateLimiter {

  /** Max burst of checkout attempts. */
  static final int CAPACITY = 10;

  /** Steady-state refill rate (tokens per second). */
  static final double REFILL_PER_SEC = 1.0;

  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

  /**
   * Consume one token for {@code accountId}. Throws {@link RateLimitedException} with a
   * {@code Retry-After} hint when the bucket is empty.
   */
  public void check(String accountId) {
    Bucket bucket = buckets.computeIfAbsent(accountId, k -> new Bucket());
    synchronized (bucket) {
      long now = System.nanoTime();
      bucket.refill(now);
      if (bucket.tokens < 1.0) {
        long retryAfter = (long) Math.ceil((1.0 - bucket.tokens) / REFILL_PER_SEC);
        throw new RateLimitedException("Too many checkout attempts; slow down", Math.max(1, retryAfter));
      }
      bucket.tokens -= 1.0;
    }
  }

  private static final class Bucket {
    double tokens = CAPACITY;
    long lastRefillNanos = System.nanoTime();

    void refill(long now) {
      double elapsedSec = (now - lastRefillNanos) / 1_000_000_000.0;
      if (elapsedSec > 0) {
        tokens = Math.min(CAPACITY, tokens + elapsedSec * REFILL_PER_SEC);
        lastRefillNanos = now;
      }
    }
  }
}
