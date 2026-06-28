package org.shakvilla.beatzmedia.audit.adapter.in.rest;

import java.util.Locale;

import org.shakvilla.beatzmedia.audit.domain.AuditEntry;

/**
 * Response DTO for the audit-log read endpoint. Matches {@code AuditEntry} in
 * {@code Frontend/src/lib/admin-data.ts} and API-CONTRACT §13.
 *
 * <pre>
 * AuditEntry { id, actor, action, target, type, time }
 * </pre>
 *
 * <ul>
 *   <li>{@code actor} — display name of the actor (from {@code actorName} if present, fallback to
 *       {@code actorId})
 *   <li>{@code target} — compound string "{@code targetType}:{@code targetId}"
 *   <li>{@code type} — lowercase AuditType wire value (e.g. {@code "user"})
 *   <li>{@code time} — ISO-8601 string
 * </ul>
 *
 * Audit ADD §6.2.
 */
public record AuditEntryDto(
    String id, String actor, String action, String target, String type, String time) {

  /**
   * Maps a domain {@link AuditEntry} to this DTO. {@code actor} is the display name if available,
   * otherwise the actor id. {@code target} is "{@code targetType}:{@code targetId}". {@code type}
   * is lowercase (matches frontend enum values). {@code time} is ISO-8601.
   */
  public static AuditEntryDto from(AuditEntry entry) {
    String actor = entry.getActorName() != null
        ? entry.getActorName()
        : entry.getActor();
    String target = entry.getTargetType() + ":" + entry.getTargetId();
    String type = entry.getType().name().toLowerCase(Locale.ROOT);
    String time = entry.getOccurredAt().toString();
    return new AuditEntryDto(entry.getId(), actor, entry.getAction(), target, type, time);
  }
}
