import '../../../../core/api/api_client.dart';
import 'dto/collection_dto.dart';

class LibraryRepository {
  LibraryRepository({ApiClient? apiClient}) : _api = apiClient ?? ApiClient();
  final ApiClient _api;

  Future<CollectionDto> getCollection() async {
    final json = await _api.get<Map<String, dynamic>>('/me/collection');
    return CollectionDto.fromJson(json);
  }

  Future<void> toggleLike(String trackId, bool add) async {
    if (add) { await _api.post<void>('/me/likes/tracks/$trackId'); } else { await _api.post<void>('/me/likes/tracks/$trackId', data: null); await _api.dio.delete('/me/likes/tracks/$trackId'); }
  }

  Future<void> followArtist(String artistId, bool add) async {
    if (add) await _api.post<void>('/me/follows/artists/$artistId'); else await _api.dio.delete('/me/follows/artists/$artistId');
  }

  Future<void> saveAlbum(String albumId, bool add) async {
    if (add) await _api.post<void>('/me/saved/albums/$albumId'); else await _api.dio.delete('/me/saved/albums/$albumId');
  }

  Future<List<UserPlaylistDto>> getPlaylists() async {
    final list = await _api.get<List<dynamic>>('/me/playlists');
    return list.map((e) => UserPlaylistDto.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<UserPlaylistDto> createPlaylist(String title, {String? firstTrackId}) async {
    final json = await _api.post<Map<String, dynamic>>('/me/playlists', data: {'title': title, if (firstTrackId != null) 'firstTrackId': firstTrackId});
    return UserPlaylistDto.fromJson(json);
  }

  Future<void> renamePlaylist(String id, String title) async => _api.dio.patch('/me/playlists/$id', data: {'title': title});
  Future<void> deletePlaylist(String id) async => _api.dio.delete('/me/playlists/$id');
  Future<void> addTrackToPlaylist(String playlistId, String trackId) async => _api.post<void>('/me/playlists/$playlistId/tracks/$trackId');
  Future<void> removeTrackFromPlaylist(String playlistId, String trackId) async => _api.dio.delete('/me/playlists/$playlistId/tracks/$trackId');

  Future<Map<String, dynamic>> getDownload(String trackId) async => _api.get<Map<String, dynamic>>('/tracks/$trackId/download');
}
