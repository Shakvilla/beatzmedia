package org.shakvilla.beatzmedia.media.domain;

/**
 * Domain event published (CDI AFTER_SUCCESS) when a {@link MediaAsset} reaches READY status.
 * Consumers (catalog/studio/podcasts) observe this to flip their track/episode to ready.
 * Carries only ids and a minimal snapshot — no JPA entities. Conventions §5 / ADD §2.
 *
 * <p>{@code durationSec} is part of that snapshot because consumers need it and cannot get it any
 * other way: a track/episode row is created as a stub with duration 0 at upload time, since the
 * ffprobe duration is only known during transcode. Without it here, every consumer would have to
 * call back into media purely to read one integer.
 */
public record MediaReady(
    MediaAssetId assetId, OwnerRef ownerRef, MediaKind kind, int durationSec) {}
