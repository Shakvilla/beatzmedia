package org.shakvilla.beatzmedia.admin.application.port.in;

import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/** Input port for {@code GET /admin/users}. Auth: any admin role (read). LLFR-ADMIN-02.1. */
public interface ListUsers {

  PagedUsersView list(UserQuery query, PageRequest page);
}
