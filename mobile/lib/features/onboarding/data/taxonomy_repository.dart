import '../../../core/api/api_client.dart';
import './taxonomy_dto.dart';

/// Taxonomy repository for fetching genres and managing fan preferences.
class TaxonomyRepository {
  TaxonomyRepository({ApiClient? apiClient})
      : _apiClient = apiClient ?? ApiClient();

  final ApiClient _apiClient;

  /// GET /v1/taxonomy?kind=genre — returns active genre terms.
  Future<List<TaxonomyTerm>> getGenres() async {
    final json = await _apiClient
        .get<List<dynamic>>('/taxonomy', query: {'kind': 'genre'});
    return (json as List)
        .map((item) => TaxonomyTerm.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  /// GET /v1/me/preferences — never 404s; returns {preferredGenres, onboarded, completedAt}.
  Future<Map<String, dynamic>> getPreferences() async {
    return await _apiClient.get<Map<String, dynamic>>('/me/preferences');
  }

  /// POST /v1/me/preferences/onboarding — 422 if fewer than 3 genres, or any genre is unknown.
  Future<Map<String, dynamic>> postOnboarding(List<String> genres) async {
    return await _apiClient.post<Map<String, dynamic>>(
        '/me/preferences/onboarding',
        data: {'genres': genres});
  }

  /// PUT /v1/me/preferences — edit the taste profile later without re-running the gate.
  Future<Map<String, dynamic>> updatePreferences(List<String> genres) async {
    return await _apiClient
        .put<Map<String, dynamic>>('/me/preferences', data: {'genres': genres});
  }
}
