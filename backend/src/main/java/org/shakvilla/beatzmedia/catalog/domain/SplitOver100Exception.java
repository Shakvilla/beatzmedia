package org.shakvilla.beatzmedia.catalog.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when split entries for a track sum to more than 100%. Maps to 422 SPLIT_OVER_100. Catalog
 * ADD §3 / INV-12.
 */
public class SplitOver100Exception extends DomainException {

  public SplitOver100Exception(String message) {
    super(ErrorCode.SPLIT_OVER_100, message);
  }
}
