package org.shakvilla.beatzmedia.library.application.port.in;

import java.util.List;

/**
 * View of a user playlist as returned by REST endpoints. Library ADD §6 / API-CONTRACT §5.
 */
public record UserPlaylistView(
    String id,
    String title,
    String description,
    List<String> trackIds,
    String createdAt) {}
