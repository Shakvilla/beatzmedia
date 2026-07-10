package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import java.math.BigDecimal;
import java.util.List;

import org.shakvilla.beatzmedia.admin.application.port.in.AdminOverviewView;

/**
 * Response DTO matching {@code AdminOverview} in {@code Frontend/src/lib/admin-data.ts}: {@code {
 * rangeLabel, kpis: { activeUsers, streams, gmv, newArtists, deltas: { users, streams, gmv } },
 * gmvByDay, needsAttention, topArtists, paymentMethods } }. Money fields ({@code kpis.gmv}, {@code
 * topArtists[].revenue}, {@code paymentMethods[].value}, {@code gmvByDay[]}) are bare decimal-cedis
 * numbers, NOT the {@code {amount,currency}} envelope — see {@link AdminOverviewView}'s javadoc for
 * the precedent this follows (Studio ADD §16 / WU-STU-4). Admin ADD §6 / §16 (WU-ADM-1).
 */
public record AdminOverviewDto(
    String rangeLabel,
    KpisDto kpis,
    List<BigDecimal> gmvByDay,
    List<AttentionItemDto> needsAttention,
    List<TopArtistDto> topArtists,
    List<PaymentMethodDto> paymentMethods) {

  public static AdminOverviewDto from(AdminOverviewView view) {
    return new AdminOverviewDto(
        view.rangeLabel(),
        KpisDto.from(view.kpis()),
        view.gmvByDay(),
        view.needsAttention().stream().map(AttentionItemDto::from).toList(),
        view.topArtists().stream().map(TopArtistDto::from).toList(),
        view.paymentMethods().stream().map(PaymentMethodDto::from).toList());
  }

  public record KpisDto(int activeUsers, long streams, BigDecimal gmv, int newArtists, DeltasDto deltas) {
    static KpisDto from(AdminOverviewView.Kpis kpis) {
      return new KpisDto(
          kpis.activeUsers(), kpis.streams(), kpis.gmv(), kpis.newArtists(), DeltasDto.from(kpis.deltas()));
    }
  }

  public record DeltasDto(int users, int streams, int gmv) {
    static DeltasDto from(AdminOverviewView.Deltas deltas) {
      return new DeltasDto(deltas.users(), deltas.streams(), deltas.gmv());
    }
  }

  public record AttentionItemDto(String id, String label, String sub, String to) {
    static AttentionItemDto from(AdminOverviewView.AttentionItem item) {
      return new AttentionItemDto(item.id(), item.label(), item.sub(), item.to());
    }
  }

  public record TopArtistDto(String name, BigDecimal revenue) {
    static TopArtistDto from(AdminOverviewView.TopArtist artist) {
      return new TopArtistDto(artist.name(), artist.revenue());
    }
  }

  public record PaymentMethodDto(String name, BigDecimal value) {
    static PaymentMethodDto from(AdminOverviewView.PaymentMethod method) {
      return new PaymentMethodDto(method.name(), method.value());
    }
  }
}
