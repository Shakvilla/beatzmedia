package org.shakvilla.beatzmedia.audit.domain;

/**
 * Categorises an audit entry by the kind of resource it describes. Pure Java, no framework imports.
 * Mirrors {@code AuditType} in {@code Frontend/src/lib/admin-data.ts}. Audit ADD / INV-10.
 */
public enum AuditType {
  USER,
  CATALOG,
  FINANCE,
  MODERATION,
  SETTINGS,
  EDITORIAL
}
