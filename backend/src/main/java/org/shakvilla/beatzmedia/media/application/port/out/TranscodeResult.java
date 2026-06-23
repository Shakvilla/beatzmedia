package org.shakvilla.beatzmedia.media.application.port.out;

import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;

/**
 * Outcome of an async transcode job, passed to {@link TranscodeJobPort#onResult}. ADD §4.2.
 * On failure, {@code ok=false} and {@code errorCode} is populated; hlsKey/previewKey are null.
 */
public record TranscodeResult(
    MediaAssetId assetId,
    ObjectKey hlsKey,
    ObjectKey previewKey,
    int durationSec,
    boolean ok,
    String errorCode) {}
