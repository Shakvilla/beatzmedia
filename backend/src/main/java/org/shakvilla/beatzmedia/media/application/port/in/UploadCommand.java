package org.shakvilla.beatzmedia.media.application.port.in;

import java.io.InputStream;

import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.OwnerRef;

/**
 * Command carrying the data needed to upload a binary asset. {@code contentHash} is a SHA-256 hex
 * digest of the body used for idempotency keying on (ownerRef, contentHash). ADD §4.1.
 */
public record UploadCommand(
    OwnerRef ownerRef,
    MediaKind kind,
    String filename,
    String declaredContentType,
    long sizeBytes,
    InputStream body,
    String contentHash) {}
