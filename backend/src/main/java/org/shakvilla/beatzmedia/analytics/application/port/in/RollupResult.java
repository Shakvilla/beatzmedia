package org.shakvilla.beatzmedia.analytics.application.port.in;

/**
 * Outcome of one {@link RollupJob#run(RollupWindow)} tick — counts for observability/logging only
 * (not asserted by consumers beyond tests). Analytics ADD §4.1.
 */
public record RollupResult(int factsProcessed, int bucketsUpserted) {

  public static final RollupResult EMPTY = new RollupResult(0, 0);
}
