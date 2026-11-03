import '../../../core/api/api_client.dart';
import '../data/taxonomy_dto.dart';
import '../data/taxonomy_repository.dart';
import '../../../core/errors/api_error.dart';
import '../../../core/session/session_provider.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// State notifier for onboarding flow — genre selection and submission.
///
/// Handles genre list fetch, selection (min 3), submit, and 422 unknown genre handling
/// (highlight/remove before retry).
class OnboardingNotifier extends StateNotifier<OnboardingState> {
  OnboardingNotifier(TaxonomyRepository repository)
      : _repository = repository,
        super(OnboardingState.initial());

  final TaxonomyRepository _repository;

  /// Fetches the genre list from the backend and populates the state.
  Future<void> loadGenres() async {
    state = state.copyWith(status: OnboardingStatus.loading);
    try {
      final genres = await _repository.getGenres();
      state = state.copyWith(
        status: OnboardingStatus.loaded,
        allGenres: genres,
        selectedGenres: [],
      );
    } on ApiError catch (e) {
      state = state.copyWith(
        status: OnboardingStatus.error,
        error: _mapApiError(e),
      );
    } catch (_) {
      state = state.copyWith(
        status: OnboardingStatus.error,
        error: 'Failed to load genres. Please check your network.',
      );
    }
  }

  /// Toggles a genre selection. Requires at least 3 genres to be selected before submit is allowed.
  void toggleGenre(TaxonomyTerm genre) {
    final List<TaxonomyTerm> selected = List.from(state.selectedGenres);
    final Set<String> selectedNames = {};
    for (final g in state.selectedGenres) {
      selectedNames.add(g.name);
    }

    if (selectedNames.contains(genre.name)) {
      // Remove deselect
      selected.removeWhere((g) => g.name == genre.name);
    } else {
      selected.add(genre);
    }

    // Validate: at least 3 must be selected for submit to be valid
    final canSubmit = selected.length >= 3;
    state = state.copyWith(selectedGenres: selected, canSubmit: canSubmit);
  }

  /// Submits the selected genres via the onboarding endpoint.
  Future<void> submitGenres() async {
    if (!state.canSubmit) return;
    state = state.copyWith(status: OnboardingStatus.submitting);
    try {
      final result = await _repository.postOnboarding(
        state.selectedGenres.map((g) => g.name).toList(),
      );
      state = state.copyWith(
        status: OnboardingStatus.success,
        preferredGenres: (result['preferredGenres'] as List?)?.map((e) => e as String).toList() ?? [],
        onboarded: result['onboarded'] as bool? ?? false,
      );
    } on ApiError catch (e) {
      // 422 with unknown genre — highlight invalid selections
      state = state.copyWith(
        status: OnboardingStatus.error,
        error: _mapApiError(e),
        showUnknownGenreHint: true,
      );
    } catch (_) {
      state = state.copyWith(
        status: OnboardingStatus.error,
        error: 'Failed to submit onboarding. Please check your network.',
      );
    }
  }

  /// Clears the unknown-genre hint, allowing the user to re-select.
  void clearGenreHint() {
    state = state.copyWith(showUnknownGenreHint: false);
  }

  String _mapApiError(ApiError e) {
    if (e.field != null && e.message.isNotEmpty) {
      return '${e.message} (field: ${e.field})';
    }
    return e.message;
  }
}

/// Current onboarding state tracked by [OnboardingNotifier].
class OnboardingState {
  const OnboardingState._({
    required this.status,
    this.allGenres = const [],
    this.selectedGenres = const [],
    this.preferredGenres = const [],
    this.onboarded = false,
    this.canSubmit = false,
    this.error,
    this.showUnknownGenreHint = false,
  });
  factory OnboardingState.initial() =>
      const OnboardingState._(
        status: OnboardingStatus.initial,
      );
  factory OnboardingState.loaded({
    required List<TaxonomyTerm> allGenres,
    required List<TaxonomyTerm> selectedGenres,
  }) =>
      OnboardingState._(
        status: OnboardingStatus.loaded,
        allGenres: allGenres,
        selectedGenres: selectedGenres,
      );
  factory OnboardingState.submitting() =>
      OnboardingState._(status: OnboardingStatus.submitting);
  factory OnboardingState.success({
    required List<String> preferredGenres,
    required bool onboarded,
  }) =>
      OnboardingState._(
        status: OnboardingStatus.success,
        preferredGenres: preferredGenres,
        onboarded: onboarded,
      );
  factory OnboardingState.error(String error) =>
      OnboardingState._(status: OnboardingStatus.error, error: error);

  final OnboardingStatus status;
  final List<TaxonomyTerm> allGenres;
  final List<TaxonomyTerm> selectedGenres;
  final List<String> preferredGenres;
  final bool onboarded;
  final bool canSubmit;
  final String? error;
  final bool showUnknownGenreHint;
  List<String> get invalidGenres => const [];

  OnboardingState copyWith({
    OnboardingStatus? status,
    List<TaxonomyTerm>? allGenres,
    List<TaxonomyTerm>? selectedGenres,
    List<String>? preferredGenres,
    bool? onboarded,
    bool? canSubmit,
    String? error,
    bool? showUnknownGenreHint,
  }) => OnboardingState._(
    status: status ?? this.status,
    allGenres: allGenres ?? this.allGenres,
    selectedGenres: selectedGenres ?? this.selectedGenres,
    preferredGenres: preferredGenres ?? this.preferredGenres,
    onboarded: onboarded ?? this.onboarded,
    canSubmit: canSubmit ?? this.canSubmit,
    error: error,
    showUnknownGenreHint: showUnknownGenreHint ?? this.showUnknownGenreHint,
  );
}

/// Enumeration of onboarding status values.
enum OnboardingStatus {
  initial,
  loading,
  loaded,
  submitting,
  success,
  error,
}

/// Provides the [OnboardingState] via [OnboardingNotifier] to the widget tree.
final onboardingProvider =
    StateNotifierProvider<OnboardingNotifier, OnboardingState>((ref) {
  final repository = TaxonomyRepository(
    apiClient: ref.read(apiClientProvider),
  );
  return OnboardingNotifier(repository);
});
