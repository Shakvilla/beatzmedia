package org.shakvilla.beatzmedia.identity.adapter.in.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for POST /v1/admin/team/invite. Identity ADD §5.1 / §6 / LLFR-IDENTITY-03.2.
 */
public record InviteRequest(
    @NotBlank(message = "email must not be blank") @Email(message = "email must be a valid address")
        String email,
    @NotBlank(message = "role must not be blank") String role) {}
