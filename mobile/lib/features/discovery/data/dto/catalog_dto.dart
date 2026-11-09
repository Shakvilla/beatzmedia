class MoneyDto {
  const MoneyDto({required this.amount, required this.currency});
  final int amount; // GHS cedis, never float for tx authority
  final String currency; // GHS
  factory MoneyDto.fromJson(Map<String, dynamic> json) => MoneyDto(
      amount: (json['amount'] as num).toInt(),
      currency: json['currency'] as String);
  Map<String, dynamic> toJson() => {'amount': amount, 'currency': currency};
}

class ArtistDto {
  const ArtistDto(
      {required this.id,
      required this.name,
      required this.image,
      this.coverImage,
      this.verified,
      this.monthlyListeners,
      this.followers,
      this.bio,
      this.location,
      this.genres});
  final String id;
  final String name;
  final String image;
  final String? coverImage;
  final bool? verified;
  final int? monthlyListeners;
  final int? followers;
  final String? bio;
  final String? location;
  final List<String>? genres;
  factory ArtistDto.fromJson(Map<String, dynamic> json) => ArtistDto(
      id: json['id'] as String,
      name: json['name'] as String,
      image: json['image'] as String,
      coverImage: json['coverImage'] as String?,
      verified: json['verified'] as bool?,
      monthlyListeners: (json['monthlyListeners'] as num?)?.toInt(),
      followers: (json['followers'] as num?)?.toInt(),
      bio: json['bio'] as String?,
      location: json['location'] as String?,
      genres: (json['genres'] as List?)?.cast<String>());
  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'image': image,
        if (coverImage != null) 'coverImage': coverImage,
        if (verified != null) 'verified': verified,
        if (monthlyListeners != null) 'monthlyListeners': monthlyListeners,
        if (bio != null) 'bio': bio
      };
}

class AlbumDto {
  const AlbumDto(
      {required this.id,
      required this.title,
      required this.artistId,
      required this.artistName,
      required this.year,
      required this.coverImage,
      required this.trackIds,
      required this.downloadable,
      this.genres});
  final String id;
  final String title;
  final String artistId;
  final String artistName;
  final int year;
  final String coverImage;
  final List<String> trackIds;
  final bool downloadable;
  final List<String>? genres;
  factory AlbumDto.fromJson(Map<String, dynamic> json) => AlbumDto(
      id: json['id'] as String,
      title: json['title'] as String,
      artistId: json['artistId'] as String,
      artistName: json['artistName'] as String,
      year: (json['year'] as num).toInt(),
      coverImage: json['coverImage'] as String,
      trackIds: (json['trackIds'] as List).cast<String>(),
      downloadable: json['downloadable'] as bool,
      genres: (json['genres'] as List?)?.cast<String>());
}

class TrackDto {
  const TrackDto(
      {required this.id,
      required this.title,
      required this.artistId,
      required this.artistName,
      required this.duration,
      required this.image,
      required this.ownership,
      required this.downloadable,
      this.albumId,
      this.albumTitle,
      this.price,
      this.plays,
      this.audioUrl,
      this.quality,
      this.year});
  final String id;
  final String title;
  final String artistId;
  final String artistName;
  final int duration;
  final String image;
  final String ownership;
  final bool downloadable;
  final String? albumId;
  final String? albumTitle;
  final MoneyDto? price;
  final int? plays;
  final String? audioUrl;
  final String? quality;
  final int? year;
  factory TrackDto.fromJson(Map<String, dynamic> json) => TrackDto(
      id: json['id'] as String,
      title: json['title'] as String,
      artistId: json['artistId'] as String,
      artistName: json['artistName'] as String,
      duration: (json['duration'] as num).toInt(),
      image: json['image'] as String,
      ownership: json['ownership'] as String,
      downloadable: json['downloadable'] as bool,
      albumId: json['albumId'] as String?,
      albumTitle: json['albumTitle'] as String?,
      price: json['price'] != null
          ? MoneyDto.fromJson(json['price'] as Map<String, dynamic>)
          : null,
      plays: (json['plays'] as num?)?.toInt(),
      audioUrl: json['audioUrl'] as String?,
      quality: json['quality'] as String?,
      year: (json['year'] as num?)?.toInt());
}

class PlaylistDto {
  const PlaylistDto(
      {required this.id,
      required this.title,
      required this.creator,
      required this.image,
      required this.isPublic,
      required this.trackIds,
      this.description,
      this.creatorAvatar,
      this.followers});
  final String id;
  final String title;
  final String creator;
  final String image;
  final bool isPublic;
  final List<String> trackIds;
  final String? description;
  final String? creatorAvatar;
  final int? followers;
  factory PlaylistDto.fromJson(Map<String, dynamic> json) => PlaylistDto(
      id: json['id'] as String,
      title: json['title'] as String,
      creator: json['creator'] as String,
      image: json['image'] as String,
      isPublic: json['isPublic'] as bool,
      trackIds: (json['trackIds'] as List).cast<String>(),
      description: json['description'] as String?,
      creatorAvatar: json['creatorAvatar'] as String?,
      followers: (json['followers'] as num?)?.toInt());
}

class BrowseCategoryDto {
  const BrowseCategoryDto(
      {required this.id, required this.title, required this.colorClass});
  final String id;
  final String title;
  final String colorClass;
  factory BrowseCategoryDto.fromJson(Map<String, dynamic> json) =>
      BrowseCategoryDto(
          id: json['id'] as String,
          title: json['title'] as String,
          colorClass: json['colorClass'] as String);
}

class ShowDto {
  const ShowDto({required this.date, required this.city, required this.venue});
  final String date;
  final String city;
  final String venue;
  factory ShowDto.fromJson(Map<String, dynamic> j) => ShowDto(
      date: j['date'] as String,
      city: j['city'] as String,
      venue: j['venue'] as String);
}

class HomeFeedDto {
  const HomeFeedDto(
      {required this.trending,
      required this.top10,
      required this.featuredAlbums,
      required this.newReleases,
      required this.popularArtists,
      required this.curatedPlaylists});
  final List<TrackDto> trending;
  final List<TrackDto> top10;
  final List<AlbumDto> featuredAlbums;
  final List<AlbumDto> newReleases;
  final List<ArtistDto> popularArtists;
  final List<PlaylistDto> curatedPlaylists;
  factory HomeFeedDto.fromJson(Map<String, dynamic> json) => HomeFeedDto(
        trending: (json['trending'] as List)
            .map((e) => TrackDto.fromJson(e as Map<String, dynamic>))
            .toList(),
        top10: (json['top10'] as List)
            .map((e) => TrackDto.fromJson(e as Map<String, dynamic>))
            .toList(),
        featuredAlbums: (json['featuredAlbums'] as List)
            .map((e) => AlbumDto.fromJson(e as Map<String, dynamic>))
            .toList(),
        newReleases: ((json['rails']?['newReleases'] ?? <dynamic>[]) as List)
            .map((e) => AlbumDto.fromJson(e as Map<String, dynamic>))
            .toList(),
        popularArtists:
            ((json['rails']?['popularArtists'] ?? <dynamic>[]) as List)
                .map((e) => ArtistDto.fromJson(e as Map<String, dynamic>))
                .toList(),
        curatedPlaylists:
            ((json['rails']?['curatedPlaylists'] ?? <dynamic>[]) as List)
                .map((e) => PlaylistDto.fromJson(e as Map<String, dynamic>))
                .toList(),
      );
}

class SearchResultDto {
  const SearchResultDto(
      {required this.tracks,
      required this.artists,
      required this.albums,
      required this.playlists});
  final List<TrackDto> tracks;
  final List<ArtistDto> artists;
  final List<AlbumDto> albums;
  final List<PlaylistDto> playlists;
  factory SearchResultDto.fromJson(Map<String, dynamic> j) => SearchResultDto(
      tracks: (j['tracks'] as List)
          .map((e) => TrackDto.fromJson(e as Map<String, dynamic>))
          .toList(),
      artists: (j['artists'] as List)
          .map((e) => ArtistDto.fromJson(e as Map<String, dynamic>))
          .toList(),
      albums: (j['albums'] as List)
          .map((e) => AlbumDto.fromJson(e as Map<String, dynamic>))
          .toList(),
      playlists: (j['playlists'] as List)
          .map((e) => PlaylistDto.fromJson(e as Map<String, dynamic>))
          .toList());
}

class ResolveResultDto {
  const ResolveResultDto(
      {required this.tracks,
      required this.artists,
      required this.albums,
      required this.playlists});
  final List<TrackDto> tracks;
  final List<ArtistDto> artists;
  final List<AlbumDto> albums;
  final List<PlaylistDto> playlists;
  factory ResolveResultDto.fromJson(Map<String, dynamic> j) => ResolveResultDto(
      tracks: (j['tracks'] as List)
          .map((e) => TrackDto.fromJson(e as Map<String, dynamic>))
          .toList(),
      artists: (j['artists'] as List)
          .map((e) => ArtistDto.fromJson(e as Map<String, dynamic>))
          .toList(),
      albums: (j['albums'] as List)
          .map((e) => AlbumDto.fromJson(e as Map<String, dynamic>))
          .toList(),
      playlists: (j['playlists'] as List)
          .map((e) => PlaylistDto.fromJson(e as Map<String, dynamic>))
          .toList());
}
