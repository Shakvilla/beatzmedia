package org.shakvilla.beatzmedia.audit.domain;

/**
 * Filter criteria for querying the audit log. All fields are optional (null = no filter). Pure
 * Java, no framework imports. Audit ADD §4.2.
 *
 * <p>Mappings to API query parameters (GET /v1/admin/audit):
 *
 * <ul>
 *   <li>{@code type} — {@code ?type=} filter by AuditType (case-insensitive)
 *   <li>{@code actor} — {@code ?actor=} free-text search on actor_id or actor_name
 *   <li>{@code q} — {@code ?q=} free-text search on action, target_type, or target_id
 * </ul>
 */
public record AuditFilter(AuditType type, String actor, String q) {

  /** No-filter: returns all entries. */
  public static AuditFilter none() {
    return new AuditFilter(null, null, null);
  }
}
