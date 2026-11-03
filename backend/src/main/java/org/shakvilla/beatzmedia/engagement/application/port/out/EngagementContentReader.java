package org.shakvilla.beatzmedia.engagement.application.port.out;

import java.util.List;

import org.shakvilla.beatzmedia.engagement.domain.ContentKind;

public interface EngagementContentReader {

  ContentSnapshot get(ContentKind kind, String contentId, String accountId);

  List<ContentSnapshot> getAll(List<ContentRef> refs, String accountId);

  record ContentRef(ContentKind kind, String contentId) {}

  record ContentSnapshot(
      ContentKind kind,
      String contentId,
      String title,
      String subtitle,
      String image,
      int durationSeconds) {}
}
