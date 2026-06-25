package org.shakvilla.beatzmedia.identity.adapter.in.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for POST /v1/auth/login. Identity ADD §5.1 / §6.
 */
public record LoginRequest(
    @NotBlank(message = "Email is required.") @Email(message = "A valid email address is required.")
        String email,
    @NotBlank(message = "Password is required.") String password) {}
