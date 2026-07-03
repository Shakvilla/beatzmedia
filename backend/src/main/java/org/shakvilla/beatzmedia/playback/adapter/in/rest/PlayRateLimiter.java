package org.shakvilla.beatzmedia.playback.adapter.in.rest;

import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

import org.shakvilla.beatzmedia.platform.domain.RateLimitedException;

/**
 * Per-caller token-bucket rate limiter guarding {@code POST /v1/tracks/:id/play} against abusive
 * bursts (Playback ADD §5.1/§9): a bucket of {@link #CAPACITY} tokens refills at
 * {@link #REFILL_PER_SEC} tokens/second; an empty bucket throws {@link RateLimitedException}
 * (→ 429 + {@code Retry-After}). Distinct from {@code RecordPlayService}'s per-(account,track)
 * de-dup window, which silently no-ops a duplicate within-window call (still 204) — this limiter
 * only trips on genuinely excessive call *volume*. In-process/per-instance, same pattern as
 * commerce's {@code CheckoutRateLimiter}.
 *
 * <p>Keyed on caller identity (JWT subject) when present, else a constant anonymous bucket key —
 * adequate for v1; the gateway/client fingerprint carries anonymous anti-abuse per the ADD.
 */
@ApplicationScoped
public class PlayRateLimiter {

  static final int CAPACITY = 20;
  static final double REFILL_PER_SEC = 2.0;

  private static final String ANONYMOUS_KEY = "__anonymous__";

  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

  public void check(String callerKey) {
    String key = (callerKey == null || callerKey.isBlank()) ? ANONYMOUS_KEY : callerKey;
    Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket());
    synchronized (bucket) {
      long now = System.nanoTime();
      bucket.refill(now);
      if (bucket.tokens < 1.0) {
        long retryAfter = (long) Math.ceil((1.0 - bucket.tokens) / REFILL_PER_SEC);
        throw new RateLimitedException("Too many play calls; slow down", Math.max(1, retryAfter));
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
