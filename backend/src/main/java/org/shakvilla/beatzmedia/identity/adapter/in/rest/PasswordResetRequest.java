package org.shakvilla.beatzmedia.identity.adapter.in.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for POST /v1/me/password/reset. Matches API-CONTRACT §2 {@code { email }}. Identity
 * ADD §5.1 / §6. Always answers 204 regardless of whether the email is registered — see
 * {@code RequestPasswordResetService} (non-enumeration, DoD §12.2).
 */
public record PasswordResetRequest(
    @NotBlank(message = "Email is required.")
    @Email(message = "A valid email address is required.")
    @Size(max = 254, message = "Email must not exceed 254 characters.")
    String email) {}
