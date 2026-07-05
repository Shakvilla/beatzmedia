package org.shakvilla.beatzmedia.analytics.domain;

/**
 * The time-bucket resolution a rollup row is stored/aggregated at. Analytics ADD §3.1.
 *
 * <p>{@link AnalyticsRange} maps deterministically onto exactly one grain (ADD §3.1): {@code 7d}
 * and {@code 28d} use {@link #DAILY}, {@code 90d} uses {@link #WEEKLY}, {@code 12m}/{@code all} use
 * {@link #MONTHLY}.
 */
public enum Grain {
  DAILY,
  WEEKLY,
  MONTHLY
}
