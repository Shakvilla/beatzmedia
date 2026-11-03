import '../../../../core/api/api_client.dart';
import '../../../../core/errors/api_error.dart';
import 'dto/catalog_dto.dart';

class CatalogRepository {
  CatalogRepository({ApiClient? apiClient}) : _api = apiClient ?? ApiClient();
  final ApiClient _api;

  Future<HomeFeedDto> getHome() async {
    final json = await _api.get<Map<String, dynamic>>('/home');
    return HomeFeedDto.fromJson(json);
  }

  Future<SearchResultDto> search(String q) async {
    final json = await _api.get<Map<String, dynamic>>('/search', query: {'q': q});
    return SearchResultDto.fromJson(json);
  }

  Future<ArtistDto> getArtist(String id) async {
    final json = await _api.get<Map<String, dynamic>>('/artists/$id');
    return ArtistDto.fromJson(json);
  }

  Future<List<TrackDto>> getArtistTracks(String id) async {
    final list = await _api.get<List<dynamic>>('/artists/$id/tracks');
    return list.map((e) => TrackDto.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<List<AlbumDto>> getArtistAlbums(String id) async {
    final list = await _api.get<List<dynamic>>('/artists/$id/albums');
    return list.map((e) => AlbumDto.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<List<ShowDto>> getArtistShows(String id) async {
    final list = await _api.get<List<dynamic>>('/artists/$id/shows');
    return list.map((e) => ShowDto.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<Map<String, dynamic>> getAlbumRaw(String id) async {
    return _api.get<Map<String, dynamic>>('/albums/$id', query: {'tracks': 'true'});
  }

  Future<AlbumDto> getAlbum(String id) async {
    final json = await getAlbumRaw(id);
    return AlbumDto.fromJson(json);
  }

  Future<List<BrowseCategoryDto>> getBrowseCategories() async {
    final list = await _api.get<List<dynamic>>('/browse-categories');
    return list.map((e) => BrowseCategoryDto.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<ResolveResultDto> resolveCatalog({List<String>? trackIds, List<String>? artistIds, List<String>? albumIds, List<String>? playlistIds}) async {
    void check(String name, List<String>? ids) {
      if (ids != null && ids.length > 200) {
        throw ApiError(status: 422, code: 'VALIDATION', message: '$name exceeds 200', field: name);
      }
    }

    check('trackIds', trackIds);
    check('artistIds', artistIds);
    check('albumIds', albumIds);
    check('playlistIds', playlistIds);
    final body = <String, dynamic>{
      if (trackIds != null) 'trackIds': trackIds,
      if (artistIds != null) 'artistIds': artistIds,
      if (albumIds != null) 'albumIds': albumIds,
      if (playlistIds != null) 'playlistIds': playlistIds,
    };
    final json = await _api.post<Map<String, dynamic>>('/catalog/resolve', data: body);
    return ResolveResultDto.fromJson(json);
  }
}
