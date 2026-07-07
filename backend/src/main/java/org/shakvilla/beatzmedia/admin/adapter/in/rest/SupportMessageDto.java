package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import org.shakvilla.beatzmedia.admin.application.port.in.SupportMessageView;

/**
 * Response DTO matching {@code SupportMessage} in {@code Frontend/src/lib/admin-data.ts}:
 * {@code { id, from, author, text, time }}. {@code time} is ISO-8601 (conventions §write-rest-
 * resource — never pre-formatted on the wire).
 */
public record SupportMessageDto(String id, String from, String author, String text, String time) {

  public static SupportMessageDto from(SupportMessageView view) {
    return new SupportMessageDto(
        view.id(), view.from(), view.author(), view.text(), view.time().toString());
  }
}
