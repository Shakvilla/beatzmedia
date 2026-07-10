package org.shakvilla.beatzmedia.admin.application.port.in;

import java.math.BigDecimal;
import java.util.List;

/**
 * Wire-shaped view for {@code GET /admin/overview}, matching {@code AdminOverview} in {@code
 * Frontend/src/lib/admin-data.ts}. Money fields are bare decimal-cedis numbers, NOT the {@code
 * {amount,currency}} envelope — matching {@code admin-data.ts}'s {@code AdminOverview.kpis.gmv:
 * number} / {@code RevenueArtist.revenue: number} / {@code PayMethod.value: number}, the same
 * convention already established for {@code StudioDefaultsView#trackPrice} (Studio ADD §16 /
 * WU-STU-4 as-built). Admin ADD §6 / §13 (WU-ADM-1).
 */
public record AdminOverviewView(
    String rangeLabel,
    Kpis kpis,
    List<BigDecimal> gmvByDay,
    List<AttentionItem> needsAttention,
    List<TopArtist> topArtists,
    List<PaymentMethod> paymentMethods) {

  public AdminOverviewView {
    gmvByDay = List.copyOf(gmvByDay);
    needsAttention = List.copyOf(needsAttention);
    topArtists = List.copyOf(topArtists);
    paymentMethods = List.copyOf(paymentMethods);
  }

  public record Kpis(int activeUsers, long streams, BigDecimal gmv, int newArtists, Deltas deltas) {}

  public record Deltas(int users, int streams, int gmv) {}

  public record AttentionItem(String id, String label, String sub, String to) {}

  public record TopArtist(String name, BigDecimal revenue) {}

  public record PaymentMethod(String name, BigDecimal value) {}
}
