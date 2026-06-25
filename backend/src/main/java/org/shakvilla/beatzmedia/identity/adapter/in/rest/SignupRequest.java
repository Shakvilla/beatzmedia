package org.shakvilla.beatzmedia.identity.adapter.in.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for POST /v1/auth/signup. Bean Validation annotations produce 422 with error.field
 * via {@link org.shakvilla.beatzmedia.platform.adapter.in.rest.ConstraintViolationExceptionMapper}.
 * Password length is intentionally NOT validated here — the application service throws
 * {@link org.shakvilla.beatzmedia.identity.domain.WeakPasswordException} (WEAK_PASSWORD, 422) so
 * the wire code is WEAK_PASSWORD, not the generic VALIDATION. Identity ADD §5.1 / §6.
 *
 * <p>Upper-bound @Size constraints prevent Argon2 amplification DoS: a multi-MB password body would
 * force the Argon2id hasher to process an arbitrarily large input. Caps: name 120, email 254
 * (RFC 5321 max), password 200 (security-authz.md §1.3 / NB-2).
 */
public record SignupRequest(
    @NotBlank(message = "Name is required.")
    @Size(max = 120, message = "Name must not exceed 120 characters.")
    String name,

    @NotBlank(message = "Email is required.")
    @Email(message = "A valid email address is required.")
    @Size(max = 254, message = "Email must not exceed 254 characters.")
    String email,

    @NotBlank(message = "Password is required.")
    @Size(max = 200, message = "Password must not exceed 200 characters.")
    String password) {}
