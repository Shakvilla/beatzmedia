package org.shakvilla.beatzmedia.platform.domain;

/**
 * Base class for all domain exceptions. Framework-free; carries an {@link ErrorCode} for mapping
 * to the HTTP error envelope at the adapter boundary. Conventions §4.
 */
public class DomainException extends RuntimeException {

  private final ErrorCode errorCode;
  private final String field;

  public DomainException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
    this.field = null;
  }

  public DomainException(ErrorCode errorCode, String message, String field) {
    super(message);
    this.errorCode = errorCode;
    this.field = field;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }

  public String getField() {
    return field;
  }
}
