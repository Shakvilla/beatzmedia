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
 *
 * <p>{@code targetId} is an additive field (WU-ADM-2): an EXACT match on {@code target_id},
 * distinct from the fuzzy {@code q} LIKE-substring search above. Added so {@code admin}'s user
 * detail page can fetch a precise action log for one account id ({@code UserDetail.actionLog})
 * without over-matching other targets that merely contain the id as a substring.
 */
public record AuditFilter(AuditType type, String actor, String q, String targetId) {

  /** No-filter: returns all entries. */
  public static AuditFilter none() {
    return new AuditFilter(null, null, null, null);
  }

  /** Convenience factory for an exact {@code targetId} match (no other filters). */
  public static AuditFilter byTargetId(String targetId) {
    return new AuditFilter(null, null, null, targetId);
  }
}
