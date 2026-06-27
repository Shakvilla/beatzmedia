package org.shakvilla.beatzmedia.identity.it;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.adapter.in.rest.AdminMemberDto;
import org.shakvilla.beatzmedia.identity.application.port.in.AdminMemberView;

/**
 * Contract test: verifies that {@link AdminMemberDto} is structurally compatible with the
 * {@code AdminMember} type in {@code Frontend/src/lib/admin-data.ts} and API-CONTRACT §14.
 *
 * <pre>
 * AdminMember { id, name, email, role, lastActive }
 * role: 'super-admin' | 'finance' | 'moderator' | 'editor' | 'support'
 * </pre>
 *
 * Identity ADD §6 / DoD §11 contract-test requirement.
 */
@Tag("unit")
class AdminMemberContractTest {

  private static final Set<String> VALID_ROLES =
      Set.of("super-admin", "finance", "moderator", "editor", "support");

  @Test
  void admin_member_dto_has_all_required_fields() {
    AdminMemberView view = new AdminMemberView(
        "test-id", "Test User", "test@example.com", "super-admin", "2026-06-26T10:00:00Z");
    AdminMemberDto dto = AdminMemberDto.from(view);

    assertNotNull(dto.id(), "id must not be null");
    assertNotNull(dto.name(), "name must not be null");
    assertNotNull(dto.email(), "email must not be null");
    assertNotNull(dto.role(), "role must not be null");
    // lastActive may be null for new members with no activity
  }

  @Test
  void admin_member_dto_field_names_match_contract() throws Exception {
    // Verify via reflection that the record component names match the contract
    var components = AdminMemberDto.class.getRecordComponents();
    var names = new java.util.HashSet<String>();
    for (var c : components) {
      names.add(c.getName());
    }
    assertTrue(names.contains("id"), "must have 'id' field");
    assertTrue(names.contains("name"), "must have 'name' field");
    assertTrue(names.contains("email"), "must have 'email' field");
    assertTrue(names.contains("role"), "must have 'role' field");
    assertTrue(names.contains("lastActive"), "must have 'lastActive' field (camelCase)");
  }

  @Test
  void all_valid_roles_are_kebab_case() {
    for (String role : VALID_ROLES) {
      AdminMemberView view = new AdminMemberView("id", "Name", "e@x.com", role, null);
      AdminMemberDto dto = AdminMemberDto.from(view);
      assertTrue(VALID_ROLES.contains(dto.role()),
          "role must be one of the valid kebab-case values, got: " + dto.role());
    }
  }

  @Test
  void super_admin_role_serializes_as_kebab_case() {
    org.shakvilla.beatzmedia.identity.domain.AdminRole role =
        org.shakvilla.beatzmedia.identity.domain.AdminRole.SUPER_ADMIN;
    org.junit.jupiter.api.Assertions.assertEquals("super-admin", role.wireValue(),
        "SUPER_ADMIN must serialize as 'super-admin' (kebab-case), not 'SUPER_ADMIN'");
  }

  @Test
  void all_admin_role_wire_values_are_in_valid_role_set() {
    for (org.shakvilla.beatzmedia.identity.domain.AdminRole r :
        org.shakvilla.beatzmedia.identity.domain.AdminRole.values()) {
      assertTrue(VALID_ROLES.contains(r.wireValue()),
          "AdminRole." + r.name() + " wire value '" + r.wireValue()
              + "' not in contract valid set");
    }
  }
}
