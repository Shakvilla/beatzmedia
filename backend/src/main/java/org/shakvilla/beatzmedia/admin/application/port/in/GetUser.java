package org.shakvilla.beatzmedia.admin.application.port.in;

/** Input port for {@code GET /admin/users/:id}. Auth: any admin role (read). LLFR-ADMIN-02.1. */
public interface GetUser {

  /**
   * @throws org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException if no such account
   *     (404)
   */
  UserDetailView get(String targetId);
}
