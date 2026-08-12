package org.shakvilla.beatzmedia.catalog.adapter.in.rest;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * Multipart form for {@code POST /v1/studio/releases/{id}/cover} — a single {@code image} part.
 * Mirrors {@code studio.ShowCoverForm}.
 */
public class ReleaseCoverForm {

  @FormParam("image")
  @PartType(MediaType.APPLICATION_OCTET_STREAM)
  public FileUpload image;
}
