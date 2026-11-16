import '../../../../core/api/api_client.dart';
import 'dto/podcast_dto.dart';
import '../../store/data/dto/store_dto.dart';

class PodcastsRepository {
  PodcastsRepository({ApiClient? apiClient}) : _api = apiClient ?? ApiClient();
  final ApiClient _api;
  Future<List<PodcastDto>> getPodcasts(
      {String? category, int page = 1, int size = 20}) async {
    final json = await _api.get<Map<String, dynamic>>('/podcasts', query: {
      if (category != null) 'category': category,
      'page': page,
      'size': size
    });
    final items = (json['items'] as List?) ?? (json['podcasts'] as List?) ?? [];
    return items
        .map((e) => PodcastDto.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<PodcastDto> getPodcast(String id) async => PodcastDto.fromJson(
      await _api.get<Map<String, dynamic>>('/podcasts/$id'));
  Future<List<PodcastEpisodeDto>> getEpisodes(String id) async {
    final list = await _api.get<List<dynamic>>('/podcasts/$id/episodes');
    return list
        .map((e) => PodcastEpisodeDto.fromJson(e as Map<String, dynamic>))
        .toList();
  }
}

class StoreRepository {
  StoreRepository({ApiClient? apiClient}) : _api = apiClient ?? ApiClient();
  final ApiClient _api;
  Future<List<StoreItemDto>> getStore(
      {String? type,
      String? genre,
      String? sort,
      int page = 1,
      int size = 20}) async {
    final json = await _api.get<Map<String, dynamic>>('/store', query: {
      if (type != null) 'type': type,
      if (genre != null) 'genre': genre,
      if (sort != null) 'sort': sort,
      'page': page,
      'size': size
    });
    final items = (json['items'] as List?) ?? [];
    return items
        .map((e) => StoreItemDto.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<StoreItemDto> getStoreItem(String id) async =>
      StoreItemDto.fromJson(await _api.get<Map<String, dynamic>>('/store/$id'));
}
