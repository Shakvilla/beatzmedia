package org.shakvilla.beatzmedia.studio.adapter.in.rest;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/** Multipart form for an artist avatar or banner upload. */
public class ProfileImageForm {

  @FormParam("image")
  @PartType(MediaType.APPLICATION_OCTET_STREAM)
  public FileUpload image;
}
