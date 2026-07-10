package org.shakvilla.beatzmedia.admin.application.port.in;

import org.shakvilla.beatzmedia.admin.domain.UserFilter;

/**
 * Query criteria for {@code GET /admin/users?q=&filter=}. Both fields optional (null = no
 * filter). Admin ADD §4.1 (LLFR-ADMIN-02.1).
 */
public record UserQuery(String q, UserFilter filter) {}
