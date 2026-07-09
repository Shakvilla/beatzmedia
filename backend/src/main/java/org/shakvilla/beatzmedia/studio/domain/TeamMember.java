package org.shakvilla.beatzmedia.studio.domain;

/**
 * A collaborator on a creator's Studio team (co-manager, label partner, pending invite, etc.).
 * Embedded in {@link StudioSettings#team()}. Studio ADD §3.
 */
public record TeamMember(String id, String name, String email, TeamRole role) {

  public TeamMember {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("TeamMember id must not be blank");
    }
    if (role == null) {
      throw new IllegalArgumentException("TeamMember role must not be null");
    }
    name = name == null ? "" : name;
    email = email == null ? "" : email;
  }
}
