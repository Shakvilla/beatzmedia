package org.shakvilla.beatzmedia.discovery.application.port.in;

import java.util.List;

public interface ManageRecommendationInteractions {

  void recordEvents(String accountId, List<EventInput> events);

  FeedbackResult feedback(String accountId, FeedbackInput input);

  void undo(String accountId, String suppressionId);

  record EventInput(String eventId, String recommendationId, String eventType) {}

  record FeedbackInput(String recommendationId, String action) {}

  record FeedbackResult(String suppressionId) {}
}
