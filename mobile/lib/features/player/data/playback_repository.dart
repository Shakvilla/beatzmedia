import '../../../../core/api/api_client.dart';
import '../../../../core/errors/api_error.dart';
import 'dto/stream_dto.dart';

class PlaybackRepository {
  PlaybackRepository({ApiClient? apiClient}) : _api = apiClient ?? ApiClient();
  final ApiClient _api;

  Future<StreamResponse> getStream(String trackId) async {
    final json = await _api.get<Map<String, dynamic>>('/tracks/$trackId/stream');
    return StreamResponse.fromJson(json);
  }

  Future<LyricsResponse> getLyrics(String trackId) async {
    try {
      final json = await _api.get<Map<String, dynamic>>('/tracks/$trackId/lyrics');
      return LyricsResponse.fromJson(json);
    } on ApiError catch (e) {
      if (e.isNotFound) return LyricsResponse.empty();
      rethrow;
    }
  }

  Future<void> recordPlay(String trackId) async {
    try {
      await _api.post<void>('/tracks/$trackId/play');
    } catch (_) {}
  }
}
