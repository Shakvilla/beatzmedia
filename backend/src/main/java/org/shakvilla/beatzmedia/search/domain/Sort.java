package org.shakvilla.beatzmedia.search.domain;

/** Sort order for search results; RELEVANCE is the default for catalog, POPULAR for store (ADD §6). */
public enum Sort {
  RELEVANCE,
  POPULAR,
  NEWEST,
  PRICE_ASC,
  PRICE_DESC
}
