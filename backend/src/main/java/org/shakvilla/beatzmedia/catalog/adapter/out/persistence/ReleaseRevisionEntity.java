package org.shakvilla.beatzmedia.catalog.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "release_revision")
public class ReleaseRevisionEntity {
  @Id public String id;

  @Column(name = "release_id", nullable = false)
  public String releaseId;

  @Column(name = "artist_id", nullable = false)
  public String artistId;

  @Column(nullable = false)
  public String status;

  @Column(nullable = false)
  public long version;

  @Column(name = "payload", nullable = false, columnDefinition = "text")
  public String payload;

  @Column(nullable = false)
  public String title;

  @Column
  public String genre;

  @Column
  public String description;

  @Column(nullable = false)
  public String visibility;

  @Column(name = "scheduled_at")
  public Instant scheduledAt;

  @Column(name = "cover_image", length = 500)
  public String coverImage;

  @Column
  public Boolean downloadable;

  @Column(name = "rejection_reason")
  public String rejectionReason;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;

  @Column(name = "submitted_at")
  public Instant submittedAt;

  @Column(name = "reviewed_at")
  public Instant reviewedAt;
}
