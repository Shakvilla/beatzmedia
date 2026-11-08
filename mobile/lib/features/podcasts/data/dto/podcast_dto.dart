import '../../../discovery/data/dto/catalog_dto.dart' show MoneyDto;

class PodcastDto {
  const PodcastDto(
      {required this.id,
      required this.title,
      required this.publisher,
      required this.image,
      required this.category,
      this.description,
      this.episodeCount,
      this.popularity,
      this.seasonPassPrice,
      this.supportsTips});
  final String id;
  final String title;
  final String publisher;
  final String image;
  final String category;
  final String? description;
  final int? episodeCount;
  final int? popularity;
  final MoneyDto? seasonPassPrice;
  final bool? supportsTips;
  factory PodcastDto.fromJson(Map<String, dynamic> j) => PodcastDto(
      id: j['id'] as String,
      title: j['title'] as String,
      publisher: j['publisher'] as String,
      image: j['image'] as String,
      category: j['category'] as String,
      description: j['description'] as String?,
      episodeCount: (j['episodeCount'] as num?)?.toInt(),
      popularity: (j['popularity'] as num?)?.toInt(),
      seasonPassPrice: j['seasonPassPrice'] != null
          ? MoneyDto.fromJson(j['seasonPassPrice'] as Map<String, dynamic>)
          : null,
      supportsTips: j['supportsTips'] as bool?);
}

class PodcastEpisodeDto {
  const PodcastEpisodeDto(
      {required this.id,
      required this.podcastId,
      required this.title,
      required this.showTitle,
      required this.image,
      required this.duration,
      required this.publishedAt,
      this.description,
      this.episodeNumber,
      this.isPremium,
      this.price,
      this.isOwned,
      this.isEarlyAccess,
      this.publicAt});
  final String id;
  final String podcastId;
  final String title;
  final String showTitle;
  final String image;
  final int duration;
  final String publishedAt;
  final String? description;
  final int? episodeNumber;
  final bool? isPremium;
  final MoneyDto? price;
  final bool? isOwned;
  final bool? isEarlyAccess;
  final String? publicAt;
  factory PodcastEpisodeDto.fromJson(Map<String, dynamic> j) =>
      PodcastEpisodeDto(
          id: j['id'] as String,
          podcastId: j['podcastId'] as String,
          title: j['title'] as String,
          showTitle: j['showTitle'] as String,
          image: j['image'] as String,
          duration: (j['duration'] as num).toInt(),
          publishedAt: j['publishedAt'] as String,
          description: j['description'] as String?,
          episodeNumber: (j['episodeNumber'] as num?)?.toInt(),
          isPremium: j['isPremium'] as bool?,
          price: j['price'] != null
              ? MoneyDto.fromJson(j['price'] as Map<String, dynamic>)
              : null,
          isOwned: j['isOwned'] as bool?,
          isEarlyAccess: j['isEarlyAccess'] as bool?,
          publicAt: j['publicAt'] as String?);
}
