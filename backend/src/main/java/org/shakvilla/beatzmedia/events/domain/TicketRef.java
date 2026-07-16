package org.shakvilla.beatzmedia.events.domain;

/**
 * Parses a commerce {@code ticket} cart {@code refId} of the form {@code "eventId:tierName"} — the
 * exact shape the SPA builds ({@code event.$eventId.tsx}: {@code `ticket:${event.id}:${tier.name}`},
 * which commerce splits on the FIRST colon, leaving {@code refId = "eventId:tierName"}). The tier is
 * identified by its NAME, not its id: the public {@code TicketTierView} deliberately never exposes
 * the tier id, so the client cannot send it (WU-COM-4).
 *
 * <p>Both the ticket price source (checkout pricing) and the ticket settlement source (issuance)
 * parse through this type so the tier they price and the tier they mint can never diverge. Event ids
 * are colon-free slugs, so the first colon is the separator and everything after it is the tier name
 * (which may itself contain spaces/parens but no colon in practice).
 */
public record TicketRef(EventId eventId, String tierName) {

  public TicketRef {
    if (tierName == null || tierName.isBlank()) {
      throw new IllegalArgumentException("tierName must not be blank");
    }
  }

  /** @throws IllegalArgumentException if {@code refId} is not {@code "eventId:tierName"}. */
  public static TicketRef parse(String refId) {
    if (refId == null) {
      throw new IllegalArgumentException("ticket refId must not be null");
    }
    int i = refId.indexOf(':');
    if (i <= 0 || i >= refId.length() - 1) {
      throw new IllegalArgumentException("ticket refId must be \"eventId:tierName\": " + refId);
    }
    return new TicketRef(new EventId(refId.substring(0, i)), refId.substring(i + 1));
  }
}
