package org.shakvilla.beatzmedia.catalog.adapter.in.rest;

import jakarta.ws.rs.FormParam;

import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * Multipart form carrying an audio file upload. Used with {@code @MultipartForm} in {@link
 * StudioReleaseResource}. Catalog ADD §5.1 / LLFR-CATALOG-02.4.
 */
public class TrackUploadForm {

  @FormParam("file")
  @PartType("application/octet-stream")
  public FileUpload file;

  public FileUpload file() {
    return file;
  }
}
