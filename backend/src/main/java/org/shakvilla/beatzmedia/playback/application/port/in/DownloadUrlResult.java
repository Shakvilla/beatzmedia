package org.shakvilla.beatzmedia.playback.application.port.in;

import java.time.Instant;

/**
 * Result of {@link GetDownloadUrl}: a signed, time-boxed URL to the lossless file, its expiry, and
 * the container the file is in ({@code "flac"}).
 *
 * <p><strong>No {@code sizeBytes}.</strong> The signing path ({@code MediaService.issueSignedUrl} →
 * {@code SignedUrl}) does not carry an object length, and the only way to obtain one today is
 * {@code ObjectStorePort.open()}, which opens a full object stream purely to read a length — an
 * extra GET against object storage on every download request to populate a display field. Reporting
 * {@code 0} for "unknown" would be a fabricated number, which is precisely the class of dishonesty
 * this feature exists to remove. Adding the field honestly needs a HEAD-style
 * {@code ObjectStorePort} method (e.g. {@code contentLength(ObjectKey)}) threaded through
 * {@code SignedUrl}; until that exists, the field is absent rather than wrong.
 */
public record DownloadUrlResult(String downloadUrl, Instant expiresAt, String format) {}
