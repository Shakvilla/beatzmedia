package org.shakvilla.beatzmedia.identity.adapter.in.rest;

import org.shakvilla.beatzmedia.identity.application.port.in.AccountView;

/**
 * Response DTO for signup and login endpoints. Matches API-CONTRACT §2
 * {@code { token, account }}. Identity ADD §6.
 */
public record AuthResponse(String token, AccountView account) {}
