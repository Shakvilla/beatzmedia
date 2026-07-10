package org.shakvilla.beatzmedia.admin.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.admin.adapter.in.rest.AdminOverviewDto;
import org.shakvilla.beatzmedia.admin.adapter.in.rest.HealthDto;
import org.shakvilla.beatzmedia.admin.application.port.in.AdminOverviewView;
import org.shakvilla.beatzmedia.admin.application.port.in.HealthView;

/**
 * Contract test: verifies that {@link AdminOverviewDto}/{@link HealthDto} are structurally
 * compatible with {@code AdminOverview}/{@code Health} in {@code Frontend/src/lib/admin-data.ts}
 * (LLFR-ADMIN-01.1/.2). Mirrors {@code SupportTicketContractTest}'s reflection-based field-name
 * check (admin ADD DoD §11 contract-test requirement).
 *
 * <pre>
 * AdminOverview {
 *   rangeLabel, kpis: { activeUsers, streams, gmv, newArtists, deltas: { users, streams, gmv } },
 *   gmvByDay, needsAttention, topArtists, paymentMethods
 * }
 * Health { status, metrics, listeners, incidents }
 * </pre>
 *
 * <p><strong>Money is a bare decimal number, NOT {@code {amount,currency}}.</strong> {@code
 * kpis.gmv}, {@code topArtists[].revenue}, {@code paymentMethods[].value}, and every {@code
 * gmvByDay[]} entry are {@link BigDecimal} — serialising as a plain JSON number, matching {@code
 * admin-data.ts}'s {@code number} types exactly (admin ADD §13 as-built deviation from the ADD's
 * illustrative {@code Money} prose, same resolution as WU-STU-4's {@code trackPrice}).
 */
@Tag("unit")
class AdminOverviewContractTest {

  @Test
  void admin_overview_dto_field_names_match_contract() {
    assertFieldNames(AdminOverviewDto.class,
        Set.of("rangeLabel", "kpis", "gmvByDay", "needsAttention", "topArtists", "paymentMethods"));
  }

  @Test
  void kpis_dto_field_names_match_contract() {
    assertFieldNames(
        AdminOverviewDto.KpisDto.class, Set.of("activeUsers", "streams", "gmv", "newArtists", "deltas"));
  }

  @Test
  void deltas_dto_field_names_match_contract() {
    assertFieldNames(AdminOverviewDto.DeltasDto.class, Set.of("users", "streams", "gmv"));
  }

  @Test
  void attention_item_dto_field_names_match_contract() {
    assertFieldNames(AdminOverviewDto.AttentionItemDto.class, Set.of("id", "label", "sub", "to"));
  }

  @Test
  void top_artist_dto_field_names_match_contract() {
    assertFieldNames(AdminOverviewDto.TopArtistDto.class, Set.of("name", "revenue"));
  }

  @Test
  void payment_method_dto_field_names_match_contract() {
    assertFieldNames(AdminOverviewDto.PaymentMethodDto.class, Set.of("name", "value"));
  }

  @Test
  void money_fields_are_bare_numbers_not_the_amount_currency_envelope() {
    assertEquals(BigDecimal.class, componentType(AdminOverviewDto.KpisDto.class, "gmv"));
    assertEquals(BigDecimal.class, componentType(AdminOverviewDto.TopArtistDto.class, "revenue"));
    assertEquals(BigDecimal.class, componentType(AdminOverviewDto.PaymentMethodDto.class, "value"));
  }

  @Test
  void health_dto_field_names_match_contract() {
    assertFieldNames(HealthDto.class, Set.of("status", "metrics", "listeners", "incidents"));
  }

  @Test
  void health_metric_dto_field_names_match_contract() {
    assertFieldNames(HealthDto.MetricDto.class, Set.of("label", "value", "sub"));
  }

  @Test
  void incident_dto_field_names_match_contract() {
    assertFieldNames(HealthDto.IncidentDto.class, Set.of("id", "title", "date", "status"));
  }

  @Test
  void admin_overview_dto_from_view_round_trips_every_field() {
    AdminOverviewView view = new AdminOverviewView(
        "last 7 days",
        new AdminOverviewView.Kpis(
            10, 200L, new BigDecimal("50.00"), 2, new AdminOverviewView.Deltas(0, 12, 34)),
        List.of(new BigDecimal("1.00"), new BigDecimal("2.00")),
        List.of(),
        List.of(new AdminOverviewView.TopArtist("Artist A", new BigDecimal("50.00"))),
        List.of());

    AdminOverviewDto dto = AdminOverviewDto.from(view);

    assertEquals("last 7 days", dto.rangeLabel());
    assertEquals(10, dto.kpis().activeUsers());
    assertEquals(200L, dto.kpis().streams());
    assertEquals(new BigDecimal("50.00"), dto.kpis().gmv());
    assertEquals(2, dto.kpis().newArtists());
    assertEquals(0, dto.kpis().deltas().users());
    assertEquals(12, dto.kpis().deltas().streams());
    assertEquals(34, dto.kpis().deltas().gmv());
    assertEquals(2, dto.gmvByDay().size());
    assertTrue(dto.needsAttention().isEmpty());
    assertEquals("Artist A", dto.topArtists().get(0).name());
    assertTrue(dto.paymentMethods().isEmpty());
  }

  @Test
  void health_dto_from_view_round_trips_the_honest_static_shape() {
    HealthDto dto = HealthDto.from(new HealthView("normal", List.of(), List.of(), List.of()));

    assertEquals("normal", dto.status());
    assertTrue(dto.metrics().isEmpty());
    assertTrue(dto.listeners().isEmpty());
    assertTrue(dto.incidents().isEmpty());
  }

  private static void assertFieldNames(Class<?> record, Set<String> expected) {
    Set<String> actual = new HashSet<>();
    for (RecordComponent c : record.getRecordComponents()) {
      actual.add(c.getName());
    }
    assertEquals(expected, actual, record.getSimpleName() + " field names must match the contract exactly");
  }

  private static Class<?> componentType(Class<?> record, String name) {
    for (RecordComponent c : record.getRecordComponents()) {
      if (c.getName().equals(name)) {
        return c.getType();
      }
    }
    throw new AssertionError("No component named " + name + " on " + record);
  }
}
