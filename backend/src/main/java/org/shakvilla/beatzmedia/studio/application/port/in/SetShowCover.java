package org.shakvilla.beatzmedia.studio.application.port.in;

import java.io.InputStream;

import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.ShowId;

/** Uploads and attaches a podcast show's cover art. */
public interface SetShowCover {

  /**
   * Stores the image via the media module and points the show at it.
   *
   * @return the stable URL now held in {@code studio_podcast_show.image}
   * @throws org.shakvilla.beatzmedia.platform.domain.NotFoundException if the show is not this
   *     artist's
   * @throws org.shakvilla.beatzmedia.platform.domain.ValidationException if the bytes are not a
   *     supported image — enforced by magic-byte sniffing in media, not by the declared
   *     content-type, which a client controls
   */
  String setCover(ArtistId artist, ShowId showId, ImageUpload image);

  /** Mirrors {@code CreateEpisode.AudioUpload}; the media module hashes and sniffs the bytes. */
  record ImageUpload(
      String filename, String contentType, long sizeBytes, InputStream body, String contentHash) {}
}
