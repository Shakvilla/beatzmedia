package org.shakvilla.beatzmedia.identity.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for POST /v1/auth/social. Matches API-CONTRACT §2
 * {@code { provider: 'facebook'|'google'|'twitter', token }}. Identity ADD §5.1 / §6.
 *
 * <p>{@code provider} is validated as a free string here and parsed to {@link
 * org.shakvilla.beatzmedia.identity.domain.SocialProvider} in the application layer so an unknown
 * provider maps to the domain-specific {@code SOCIAL_TOKEN_INVALID} (401) rather than the generic
 * {@code VALIDATION} (422) — mirrors the {@code SignupRequest}/{@code WeakPasswordException}
 * pattern used for WU-IDN-1.
 */
public record SocialRequest(
    @NotBlank(message = "Provider is required.")
    @Size(max = 32, message = "Provider must not exceed 32 characters.")
    String provider,

    @NotBlank(message = "Token is required.")
    @Size(max = 4096, message = "Token must not exceed 4096 characters.")
    String token) {}
