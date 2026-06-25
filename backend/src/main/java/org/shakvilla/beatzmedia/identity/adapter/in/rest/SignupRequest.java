package org.shakvilla.beatzmedia.identity.adapter.in.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for POST /v1/auth/signup. Bean Validation annotations produce 422 with error.field
 * via {@link org.shakvilla.beatzmedia.platform.adapter.in.rest.ConstraintViolationExceptionMapper}.
 * Password length is intentionally NOT validated here — the application service throws
 * {@link org.shakvilla.beatzmedia.identity.domain.WeakPasswordException} (WEAK_PASSWORD, 422) so
 * the wire code is WEAK_PASSWORD, not the generic VALIDATION. Identity ADD §5.1 / §6.
 */
public record SignupRequest(
    @NotBlank(message = "Name is required.") String name,
    @NotBlank(message = "Email is required.") @Email(message = "A valid email address is required.")
        String email,
    @NotBlank(message = "Password is required.") String password) {}
