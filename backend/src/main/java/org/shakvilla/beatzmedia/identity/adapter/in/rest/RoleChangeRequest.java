package org.shakvilla.beatzmedia.identity.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for PATCH /v1/admin/team/:id. Identity ADD §5.1 / §6 / LLFR-IDENTITY-03.3.
 */
public record RoleChangeRequest(
    @NotBlank(message = "role must not be blank") String role) {}
