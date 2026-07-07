package org.shakvilla.beatzmedia.admin.domain;

/**
 * A single ordered slot on the home "featured" rail. Pure Java, no framework imports. Feeds
 * {@code /home} (the {@code catalog} reader consumes positions — this module never reads catalog
 * tables). Admin ADD §3 / LLFR-ADMIN-06.1.
 *
 * <p>Fields: {@code id}, {@code position} (1-based, unique, defines rail order), {@code title},
 * {@code note} (nullable free text, e.g. "manual · 64 tracks"), {@code isSponsored}.
 */
public final class FeaturedSlot {

  private final String id;
  private final int position;
  private final String title;
  private final String note;
  private final boolean sponsored;

  public FeaturedSlot(String id, int position, String title, String note, boolean sponsored) {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("FeaturedSlot id must not be blank");
    }
    if (title == null || title.isBlank()) {
      throw new BlankFeaturedSlotTitleException();
    }
    if (position < 1) {
      throw new IllegalArgumentException("FeaturedSlot position must be >= 1");
    }
    this.id = id;
    this.position = position;
    this.title = title;
    this.note = note;
    this.sponsored = sponsored;
  }

  public String getId() {
    return id;
  }

  public int getPosition() {
    return position;
  }

  public String getTitle() {
    return title;
  }

  public String getNote() {
    return note;
  }

  public boolean isSponsored() {
    return sponsored;
  }
}
