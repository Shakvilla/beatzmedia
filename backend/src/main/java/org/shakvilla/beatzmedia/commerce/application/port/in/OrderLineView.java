package org.shakvilla.beatzmedia.commerce.application.port.in;

/**
 * Wire shape for one order line, including the display snapshot taken at checkout ({@code
 * subtitle}/{@code image} — nullable, WU-COM-3). API-CONTRACT.md §6.
 */
public record OrderLineView(
    String id,
    String kind,
    String refId,
    String title,
    String subtitle,
    String image,
    MoneyView unitPrice,
    int quantity) {}
