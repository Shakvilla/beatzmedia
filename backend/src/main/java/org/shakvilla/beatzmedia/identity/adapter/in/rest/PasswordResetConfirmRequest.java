package org.shakvilla.beatzmedia.identity.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for POST /v1/auth/password/reset — redeem a reset token and set a new password.
 *
 * <p>No {@code @Size(min = 8)} on the password: the minimum is enforced in
 * {@code ResetPasswordService} so it answers with the same {@code WEAK_PASSWORD} code as signup,
 * rather than a generic bean-validation 422. The upper bound is here only to stop an unbounded body
 * reaching the hasher.
 */
public record PasswordResetConfirmRequest(
    @NotBlank(message = "Reset token is required.")
    @Size(max = 512, message = "Reset token is malformed.")
    String token,
    @NotBlank(message = "A new password is required.")
    @Size(max = 200, message = "Password must not exceed 200 characters.")
    String password) {}
