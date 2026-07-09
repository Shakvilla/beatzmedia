package org.shakvilla.beatzmedia.studio.application.port.in;

import java.math.BigDecimal;

/**
 * Wire-shaped release defaults, shared by {@link StudioSettingsView} (output) and {@link
 * SaveStudioSettingsCommand} (input). {@code trackPrice} is a bare decimal-cedis number, not the
 * {@code {amount,currency}} money envelope — matching {@code Frontend/src/lib/studio-data.ts}'s
 * {@code StudioSettings.defaults.trackPrice: number} and the same convention already established
 * for {@code StudioEpisodeDto.price} (Studio ADD §14 as-built notes). Studio ADD §6.
 */
public record StudioDefaultsView(
    BigDecimal trackPrice, String releaseVisibility, boolean autoExplicit, boolean allowOffers) {}
