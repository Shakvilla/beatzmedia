package org.shakvilla.beatzmedia.notifications.application.service;

/**
 * In-module CDI event fired by {@link NotifyService} whenever a NEW notification row is durably
 * created (never fired for a dedupe-guard no-op replay — INV-N4). {@link DispatchSubscriber}
 * observes this {@code AFTER_SUCCESS} to fan out to the enabled external channels (WU-NOT-2,
 * LLFR-NOTIF-02.1), keeping dispatch on the SAME creation path as the in-app feed rather than a
 * second, independent observer of the source domain events (hard requirement: no double logic).
 *
 * <p>Carries only ids + the already-rendered title/body — {@link DispatchSubscriber} builds
 * {@code EmailMessage}/{@code SmsMessage} payloads straight from these fields, no re-fetch needed.
 */
public record NotificationCreated(String notificationId, String recipientId, String title, String body) {}
