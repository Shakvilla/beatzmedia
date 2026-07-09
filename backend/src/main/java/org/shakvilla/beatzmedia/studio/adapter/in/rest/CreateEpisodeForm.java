package org.shakvilla.beatzmedia.studio.adapter.in.rest;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * Multipart form for {@code POST /v1/studio/podcasts/episodes}: an {@code audio} file part + a
 * {@code data} JSON part deserialized straight to {@link StudioPodcastResource.CreateEpisodeBody}.
 * {@code cover} is NOT a second file part — it is a plain URL string carried inside {@code data}
 * (API-CONTRACT.md §11). Mirrors {@code catalog.adapter.in.rest.TrackUploadForm} for the file part.
 * Studio ADD §5.1 (WU-STU-2).
 */
public class CreateEpisodeForm {

  @FormParam("audio")
  @PartType(MediaType.APPLICATION_OCTET_STREAM)
  public FileUpload audio;

  @FormParam("data")
  @PartType(MediaType.APPLICATION_JSON)
  public StudioPodcastResource.CreateEpisodeBody data;
}
