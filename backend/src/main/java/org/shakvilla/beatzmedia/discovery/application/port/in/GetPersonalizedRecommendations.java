package org.shakvilla.beatzmedia.discovery.application.port.in;

import java.util.List;

public interface GetPersonalizedRecommendations {

  RecommendationPage get(String accountId, int limit);

  record RecommendationPage(List<RecommendationItem> items) {}

  record RecommendationItem(
      String kind,
      String contentId,
      String creatorId,
      String title,
      String subtitle,
      String image,
      String route,
      String reason) {}
}
