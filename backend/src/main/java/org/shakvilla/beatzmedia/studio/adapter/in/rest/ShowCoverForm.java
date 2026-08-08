package org.shakvilla.beatzmedia.studio.adapter.in.rest;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * Multipart form for {@code POST /v1/studio/podcasts/shows/{id}/cover} — a single {@code image}
 * file part.
 *
 * <p>The first image upload in the application. Every image before this was a seeded URL or, in the
 * release wizard, a {@code blob:} object URL that never left the browser — which is why a cover
 * could be "added" in the UI and still be absent server-side.
 */
public class ShowCoverForm {

  @FormParam("image")
  @PartType(MediaType.APPLICATION_OCTET_STREAM)
  public FileUpload image;
}
