package org.shakvilla.beatzmedia.media.application.port.out;

import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;

/**
 * Specification for an async transcode job. ADD §4.2.
 */
public record TranscodeJob(MediaAssetId assetId, ObjectKey original, int previewSeconds) {}
