package org.shakvilla.beatzmedia.identity.application.port.in;

/**
 * Read-model for an admin-team member. Returned by all four admin-team use-case ports. Maps
 * directly to {@code AdminMemberDto} in the REST adapter. Identity ADD §4.1.
 *
 * @param id the admin_member PK id
 * @param name display name from the linked account
 * @param email email from the linked account
 * @param role kebab-case role string (e.g. {@code "super-admin"})
 * @param lastActive ISO-8601 string of the member's last-active timestamp, or {@code null}
 */
public record AdminMemberView(String id, String name, String email, String role, String lastActive) {
}
