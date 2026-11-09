package org.shakvilla.beatzmedia.discovery.application.port.out;

import java.time.Instant;
import java.util.List;

import org.shakvilla.beatzmedia.engagement.application.port.in.GetDiscoveryEngagementMetrics.ContentRef;

/** Published content grouped at the same level that fans see in discovery. */
public interface CollectionContent {

  List<Group> resolve(List<ContentRef> engagedContent);

  record Group(
      String kind,
      String id,
      String creatorId,
      String title,
      String subtitle,
      String image,
      String route,
      Instant publishedAt,
      List<String> tags,
      List<ContentRef> members,
      String originCountry) {
    public Group(
        String kind, String id, String creatorId, String title, String subtitle, String image,
        String route, Instant publishedAt, List<String> tags, List<ContentRef> members) {
      this(kind, id, creatorId, title, subtitle, image, route, publishedAt, tags, members, null);
    }

    public String key() {
      return kind + ":" + id;
    }
  }
}
