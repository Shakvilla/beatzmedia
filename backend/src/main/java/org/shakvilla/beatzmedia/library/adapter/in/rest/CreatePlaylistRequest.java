package org.shakvilla.beatzmedia.library.adapter.in.rest;

/** Request body for POST /v1/me/playlists. Library ADD §6. */
public record CreatePlaylistRequest(String title) {}
