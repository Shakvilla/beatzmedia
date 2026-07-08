package org.shakvilla.beatzmedia.store.application.port.in;

import java.util.List;

/**
 * Read-model / DTO for a beat license tier. Field names match the {@code LicenseOption}
 * TypeScript type in {@code Frontend/src/types/index.ts} and {@code API-CONTRACT.md} §7. Store
 * ADD §6.
 */
public record LicenseOptionView(String tier, String label, MoneyView price, List<String> features, String terms) {}
