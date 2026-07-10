package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import java.util.List;

import org.shakvilla.beatzmedia.admin.application.port.in.PagedUsersView;

/**
 * Response DTO for {@code GET /admin/users}: {@code { items, page, size, total, counts } }. Admin
 * ADD §6 (LLFR-ADMIN-02.1).
 */
public record PagedUsersDto(
    List<AdminUserRowDto> items, int page, int size, long total, UserCountsDto counts) {

  public static PagedUsersDto from(PagedUsersView view) {
    return new PagedUsersDto(
        view.items().stream().map(AdminUserRowDto::from).toList(),
        view.page(),
        view.size(),
        view.total(),
        UserCountsDto.from(view.counts()));
  }

  /** Matches {@code admin-data.ts}'s {@code USER_COUNTS}: {@code { all, fans, artists, verified, suspended } }. */
  public record UserCountsDto(int all, int fans, int artists, int verified, int suspended) {
    static UserCountsDto from(org.shakvilla.beatzmedia.admin.application.port.out.IdentityReader.UserCounts c) {
      return new UserCountsDto(c.all(), c.fans(), c.artists(), c.verified(), c.suspended());
    }
  }
}
