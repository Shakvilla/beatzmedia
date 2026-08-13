package org.shakvilla.beatzmedia.playback.adapter.in.rest;

import java.time.Instant;

/**
 * Wire DTO for {@code GET /v1/tracks/:id/download}. {@code format} is the container of the file
 * behind {@code downloadUrl} ({@code "flac"}), so a client can name the saved file correctly
 * without parsing the signed URL.
 *
 * <p>No {@code sizeBytes}: see {@code DownloadUrlResult} for why an unknown size is omitted rather
 * than reported as {@code 0}.
 */
public record DownloadUrlResponse(String downloadUrl, Instant expiresAt, String format) {}
