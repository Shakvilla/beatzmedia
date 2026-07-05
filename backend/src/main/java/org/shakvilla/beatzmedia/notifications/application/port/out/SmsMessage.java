package org.shakvilla.beatzmedia.notifications.application.port.out;

/**
 * Outbound SMS payload for {@link SmsSender#send}. Framework-free DTO. Notifications ADD §4.2.
 *
 * @param to recipient phone number
 * @param body message text
 * @param idempotencyKey provider-facing idempotency key (notification id + channel)
 */
public record SmsMessage(String to, String body, String idempotencyKey) {}
