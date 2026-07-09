package org.shakvilla.beatzmedia.studio.application.port.in;

/**
 * Wire-shaped Studio team member, shared by {@link StudioSettingsView} (output) and {@link
 * SaveStudioSettingsCommand} (input). {@code role} is one of {@code Owner|Manager|Label|Invited} —
 * validated as a Bean Validation {@code @Pattern} on the inbound DTO (422 {@code VALIDATION}) and
 * mapped to the domain {@code TeamRole} enum in {@code StudioSettingsMapper}. Studio ADD §6.
 */
public record TeamMemberView(String id, String name, String email, String role) {}
