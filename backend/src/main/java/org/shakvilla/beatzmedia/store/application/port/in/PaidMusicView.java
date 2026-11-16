package org.shakvilla.beatzmedia.store.application.port.in;

/** A live, priced catalog track or release offered through the Store music rail. */
public record PaidMusicView(
    String id,
    String kind,
    String title,
    String artistName,
    String artistId,
    String image,
    MoneyView price,
    String genre,
    long popularity,
    String releasedAt) {}
