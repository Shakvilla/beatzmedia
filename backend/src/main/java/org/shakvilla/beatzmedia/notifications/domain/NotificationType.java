package org.shakvilla.beatzmedia.notifications.domain;

/**
 * Notification category, verbatim from the frontend {@code NotificationType} union
 * (`notifications-context.tsx`) and PRD §6.10. Notifications ADD §3.
 */
public enum NotificationType {
  sale,
  tip,
  follower,
  payout,
  release,
  system
}
