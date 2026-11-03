package org.shakvilla.beatzmedia.catalog.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.catalog.domain.ReleaseRevision;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseRevisionStatus;
import org.shakvilla.beatzmedia.catalog.domain.Visibility;

@ApplicationScoped
public class ReleaseRevisionMapper {
  private final ObjectMapper json;

  @Inject
  public ReleaseRevisionMapper(ObjectMapper json) {
    this.json = json;
  }

  public ReleaseRevision toDomain(ReleaseRevisionEntity entity) {
    try {
      ReleaseRevision payload = json.readValue(entity.payload, ReleaseRevision.class);
      return new ReleaseRevision(
          entity.id,
          entity.releaseId,
          entity.artistId,
          ReleaseRevisionStatus.valueOf(entity.status),
          entity.version,
          entity.title,
          entity.genre,
          entity.description,
          Visibility.fromDbValue(entity.visibility),
          entity.scheduledAt,
          entity.coverImage,
          entity.downloadable,
          payload.tracks(),
          entity.rejectionReason,
          entity.createdAt,
          entity.updatedAt,
          entity.submittedAt,
          entity.reviewedAt);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not read release revision " + entity.id, e);
    }
  }

  public ReleaseRevisionEntity toEntity(ReleaseRevision revision) {
    try {
      ReleaseRevisionEntity entity = new ReleaseRevisionEntity();
      entity.id = revision.id();
      entity.releaseId = revision.releaseId();
      entity.artistId = revision.artistId();
      entity.status = revision.status().name();
      entity.version = revision.version();
      entity.payload = json.writeValueAsString(revision);
      entity.title = revision.title();
      entity.genre = revision.genre();
      entity.description = revision.description();
      entity.visibility = revision.visibility().toDbValue();
      entity.scheduledAt = revision.scheduledAt();
      entity.coverImage = revision.coverImage();
      entity.downloadable = revision.downloadable();
      entity.rejectionReason = revision.rejectionReason();
      entity.createdAt = revision.createdAt();
      entity.updatedAt = revision.updatedAt();
      entity.submittedAt = revision.submittedAt();
      entity.reviewedAt = revision.reviewedAt();
      return entity;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not write release revision " + revision.id(), e);
    }
  }
}
