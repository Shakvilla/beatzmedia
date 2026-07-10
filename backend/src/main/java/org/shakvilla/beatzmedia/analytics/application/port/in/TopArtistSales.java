package org.shakvilla.beatzmedia.analytics.application.port.in;

/**
 * One artist's summed {@code sales_minor} within a {@link GetPlatformSalesSummary} window. {@code
 * artistId} is the raw account id (never resolved to a display name here — that is a presentation
 * concern of the calling module, e.g. {@code admin}). Analytics ADD §4.1 (WU-ADM-1 addition).
 */
public record TopArtistSales(String artistId, long salesMinor) {}
