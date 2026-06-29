package org.shakvilla.beatzmedia.library.adapter.in.rest;

/** Request body for PATCH /v1/me/playlists/:id. Library ADD §6. */
public record RenamePlaylistRequest(String title) {}
