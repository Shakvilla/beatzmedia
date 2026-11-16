package org.shakvilla.beatzmedia.engagement.adapter.in.rest;

import jakarta.validation.constraints.NotNull;

public record ListeningSettingsRequest(@NotNull Boolean historyEnabled) {}
