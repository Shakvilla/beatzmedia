package org.shakvilla.beatzmedia.media.domain;

/**
 * Domain event published (CDI AFTER_SUCCESS) when a {@link MediaAsset} reaches READY status.
 * Consumers (catalog/studio/podcasts) observe this to flip their track/episode to ready.
 * Carries only ids and minimal snapshot — no JPA entities. Conventions §5 / ADD §2.
 */
public record MediaReady(MediaAssetId assetId, OwnerRef ownerRef, MediaKind kind) {}
