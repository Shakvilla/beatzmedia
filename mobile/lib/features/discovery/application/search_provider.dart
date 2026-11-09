import 'dart:async';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../data/catalog_repository.dart';
import '../data/dto/catalog_dto.dart';
import 'home_provider.dart' show catalogRepositoryProvider;

class SearchState {
  const SearchState(
      {this.query = '', this.result, this.isSearching = false, this.error});
  final String query;
  final SearchResultDto? result;
  final bool isSearching;
  final Object? error;
  SearchState copyWith(
          {String? query,
          SearchResultDto? result,
          bool? isSearching,
          Object? error}) =>
      SearchState(
          query: query ?? this.query,
          result: result,
          isSearching: isSearching ?? this.isSearching,
          error: error);
}

class SearchNotifier extends StateNotifier<SearchState> {
  SearchNotifier(this._repo) : super(const SearchState());
  final CatalogRepository _repo;
  Timer? _debounce;
  CancelToken? _cancelToken;

  void search(String q) {
    _debounce?.cancel();
    final trimmed = q.trim();
    if (trimmed.length < 2) {
      _cancelToken?.cancel();
      state = const SearchState(query: '', result: null, isSearching: false);
      return;
    }
    _debounce = Timer(const Duration(milliseconds: 300), () async {
      _cancelToken?.cancel();
      _cancelToken = CancelToken();
      state = state.copyWith(query: trimmed, isSearching: true, error: null);
      try {
        final res = await _repo.search(trimmed);
        if (!_cancelToken!.isCancelled) {
          state = state.copyWith(result: res, isSearching: false);
        }
      } catch (e) {
        if (e is DioException && CancelToken.isCancel(e)) return;
        state = state.copyWith(isSearching: false, error: e);
      }
    });
  }

  void clear() {
    _debounce?.cancel();
    _cancelToken?.cancel();
    state = const SearchState();
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _cancelToken?.cancel();
    super.dispose();
  }
}

final searchProvider =
    StateNotifierProvider<SearchNotifier, SearchState>((ref) {
  return SearchNotifier(ref.watch(catalogRepositoryProvider));
});
