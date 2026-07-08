package org.shakvilla.beatzmedia.events.application.port.in;

import java.util.List;

/**
 * Read-model / DTO for a ticket tier. Field names match the {@code TicketTier} TypeScript type in
 * {@code Frontend/src/types/index.ts} and {@code API-CONTRACT.md} §9. Note: {@code capacity}/{@code
 * sold} counters are internal and NOT serialized — only the derived {@code soldOut} is exposed
 * (Events ADD §6). The tier's internal id is likewise not part of this wire shape.
 */
public record TicketTierView(String name, MoneyView price, List<String> perks, Boolean soldOut) {}
