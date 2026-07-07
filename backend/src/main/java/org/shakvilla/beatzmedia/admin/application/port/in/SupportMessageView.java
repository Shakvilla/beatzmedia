package org.shakvilla.beatzmedia.admin.application.port.in;

import java.time.Instant;

/**
 * Read view of a single thread message — matches the frontend {@code SupportMessage} shape
 * ({@code id, from, author, text, time}). Admin ADD §6 / LLFR-ADMIN-08.1.
 */
public record SupportMessageView(String id, String from, String author, String text, Instant time) {}
