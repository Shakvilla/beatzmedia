import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../data/library_repository.dart';
import '../data/dto/collection_dto.dart';

class LibraryState {
  const LibraryState({this.collection, this.isLoading = false, this.error});
  final CollectionDto? collection; final bool isLoading; final Object? error;
  LibraryState copyWith({CollectionDto? collection, bool? isLoading, Object? error}) => LibraryState(collection: collection ?? this.collection, isLoading: isLoading ?? this.isLoading, error: error);
}

class LibraryNotifier extends StateNotifier<LibraryState> {
  LibraryNotifier(this._repo) : super(const LibraryState());
  final LibraryRepository _repo;
  Future<void> load() async { state = state.copyWith(isLoading: true); try { final col = await _repo.getCollection(); state = LibraryState(collection: col, isLoading: false); } catch (e) { state = state.copyWith(isLoading: false, error: e); } }
  Future<void> toggleLike(String trackId, bool wasLiked) async { final col = state.collection; if (col == null) return; final optimistic = wasLiked ? (col.likedTracks..remove(trackId)) : (col.likedTracks..add(trackId)); state = state.copyWith(collection: CollectionDto(likedTracks: List.from(optimistic), followedArtists: col.followedArtists, followedPlaylists: col.followedPlaylists, followedShows: col.followedShows, savedAlbums: col.savedAlbums, ownedTracks: col.ownedTracks, downloadableTracks: col.downloadableTracks, userPlaylists: col.userPlaylists)); try { await _repo.toggleLike(trackId, !wasLiked); } catch (_) { await load(); } }
}

final libraryRepositoryProvider = Provider<LibraryRepository>((ref) => LibraryRepository());
final libraryProvider = StateNotifierProvider<LibraryNotifier, LibraryState>((ref) => LibraryNotifier(ref.watch(libraryRepositoryProvider))..load());
