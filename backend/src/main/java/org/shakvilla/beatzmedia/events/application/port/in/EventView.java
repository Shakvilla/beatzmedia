package org.shakvilla.beatzmedia.events.application.port.in;

import java.util.List;

/**
 * Read-model / DTO for an event. Field names match the {@code Event} TypeScript type in {@code
 * Frontend/src/types/index.ts} and {@code API-CONTRACT.md} §9. {@code status} is server-derived
 * from live tier availability, never a stored display string (INV-EVT-2). Events ADD §6.
 */
public record EventView(
    String id,
    String title,
    String artistName,
    String artistId,
    List<String> lineup,
    String image,
    String date,
    String doorsTime,
    String venue,
    String city,
    String region,
    String status,
    String category,
    String description,
    List<TicketTierView> ticketTiers,
    Integer popularity,
    String ageRestriction) {}
