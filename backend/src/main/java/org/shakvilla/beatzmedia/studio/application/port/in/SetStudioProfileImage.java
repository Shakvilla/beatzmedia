package org.shakvilla.beatzmedia.studio.application.port.in;

import java.io.InputStream;

import org.shakvilla.beatzmedia.studio.domain.ArtistId;

/** Uploads and persists one image on the authenticated artist's studio profile. */
public interface SetStudioProfileImage {

  String upload(ArtistId artist, ImageType type, ImageUpload image);

  enum ImageType { AVATAR, BANNER }

  record ImageUpload(
      String filename,
      String contentType,
      long sizeBytes,
      InputStream body,
      String contentHash) {}
}
