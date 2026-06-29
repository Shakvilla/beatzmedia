package org.shakvilla.beatzmedia.library.domain;

/**
 * Thrown when a like/follow/save target does not exist in the catalog. The REST adapter maps this
 * to 404 with a code specific to the target kind. Library ADD §5.1.
 */
public class TargetNotFoundException extends RuntimeException {

  private final String code;

  public TargetNotFoundException(String code, String targetId) {
    super(code + ": " + targetId);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
