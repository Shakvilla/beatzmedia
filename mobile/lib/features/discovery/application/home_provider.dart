import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../data/catalog_repository.dart';
import '../data/dto/catalog_dto.dart';

final catalogRepositoryProvider =
    Provider<CatalogRepository>((ref) => CatalogRepository());

class HomeNotifier extends AsyncNotifier<HomeFeedDto> {
  @override
  Future<HomeFeedDto> build() async =>
      ref.read(catalogRepositoryProvider).getHome();

  Future<void> refresh() async {
    final previous = state.value;
    state = previous != null ? AsyncData(previous) : const AsyncLoading();
    try {
      final fresh = await ref.read(catalogRepositoryProvider).getHome();
      state = AsyncData(fresh);
    } catch (e, st) {
      if (previous != null) {
        state = AsyncData(previous);
      } else {
        state = AsyncError(e, st);
      }
    }
  }
}

final homeProvider =
    AsyncNotifierProvider<HomeNotifier, HomeFeedDto>(HomeNotifier.new);
