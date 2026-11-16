package org.shakvilla.beatzmedia.notifications.domain;

/** Durable lifecycle of a publication notification fanout job. */
public enum FanoutStatus {
  pending,
  completed
}
