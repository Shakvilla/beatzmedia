package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import java.util.List;

import org.shakvilla.beatzmedia.admin.application.port.in.HealthView;

/**
 * Response DTO matching {@code Health} in {@code Frontend/src/lib/admin-data.ts}: {@code { status,
 * metrics, listeners, incidents } }. See {@link HealthView}'s javadoc — this is almost entirely a
 * Category B honest-empty placeholder (no observability pipeline exists). Admin ADD §6 / §16
 * (WU-ADM-1).
 */
public record HealthDto(String status, List<MetricDto> metrics, List<Double> listeners, List<IncidentDto> incidents) {

  public static HealthDto from(HealthView view) {
    return new HealthDto(
        view.status(),
        view.metrics().stream().map(MetricDto::from).toList(),
        view.listeners(),
        view.incidents().stream().map(IncidentDto::from).toList());
  }

  public record MetricDto(String label, String value, String sub) {
    static MetricDto from(HealthView.Metric metric) {
      return new MetricDto(metric.label(), metric.value(), metric.sub());
    }
  }

  public record IncidentDto(String id, String title, String date, String status) {
    static IncidentDto from(HealthView.Incident incident) {
      return new IncidentDto(incident.id(), incident.title(), incident.date(), incident.status());
    }
  }
}
