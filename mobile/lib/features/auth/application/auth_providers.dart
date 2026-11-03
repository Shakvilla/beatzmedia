import '../../../core/errors/api_error.dart';
import '../../../core/api/dto/auth_dto.dart';
import '../../../core/session/session_provider.dart';
import '../data/auth_repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// StateNotifier for auth flows (login, signup, password reset).
///
/// Exposes loading/error state and calls sessionProvider.notifier.setToken on success.
class AuthNotifier extends StateNotifier<AuthState> {
  AuthNotifier({required AuthRepository repository})
      : _repository = repository,
        super(AuthState.initial());

  final AuthRepository _repository;

  Future<void> login(String email, String password) async {
    state = state.copyWith(status: AuthStatus.loading, error: null);
    try {
      final account = await _repository.login(email, password);
      state = state.copyWith(
        status: AuthStatus.authenticated,
        account: account,
      );
    } on ApiError catch (e) {
      state = state.copyWith(
        status: AuthStatus.error,
        error: _mapApiError(e),
      );
    } catch (_) {
      state = state.copyWith(
        status: AuthStatus.error,
        error: 'Failed to connect. Please check your network.',
      );
    }
  }

  Future<void> signup(String name, String email, String password) async {
    state = state.copyWith(status: AuthStatus.loading, error: null);
    try {
      final account = await _repository.signup(name, email, password);
      state = state.copyWith(
        status: AuthStatus.authenticated,
        account: account,
      );
    } on ApiError catch (e) {
      state = state.copyWith(
        status: AuthStatus.error,
        error: _mapApiError(e),
      );
    } catch (_) {
      state = state.copyWith(
        status: AuthStatus.error,
        error: 'Failed to connect. Please check your network.',
      );
    }
  }

  Future<void> requestPasswordReset(String email) async {
    state = state.copyWith(status: AuthStatus.loading, error: null);
    try {
      await _repository.requestPasswordReset(email);
      state = state.copyWith(status: AuthStatus.resetRequested);
    } on ApiError catch (e) {
      state = state.copyWith(
        status: AuthStatus.error,
        error: _mapApiError(e),
      );
    } catch (_) {
      state = state.copyWith(
        status: AuthStatus.error,
        error: 'Failed to connect. Please check your network.',
      );
    }
  }

  Future<void> resetPassword(String token, String password) async {
    state = state.copyWith(status: AuthStatus.loading, error: null);
    try {
      await _repository.resetPassword(token, password);
      state = state.copyWith(
        status: AuthStatus.resetCompleted,
      );
    } on ApiError catch (e) {
      state = state.copyWith(
        status: AuthStatus.error,
        error: _mapApiError(e),
      );
    } catch (_) {
      state = state.copyWith(
        status: AuthStatus.error,
        error: 'Failed to connect. Please check your network.',
      );
    }
  }

  String _mapApiError(ApiError e) {
    if (e.field != null && e.message.isNotEmpty) {
      return '${e.message} (field: ${e.field})';
    }
    return e.message;
  }
}

/// Current auth state tracked by [AuthNotifier].
class AuthState {
  const AuthState._({required this.status, this.account, this.error});
  factory AuthState.initial() => const AuthState._(status: AuthStatus.initial);
  factory AuthState.authenticated(Account account) =>
      AuthState._(status: AuthStatus.authenticated, account: account);
  factory AuthState.error(String error) =>
      AuthState._(status: AuthStatus.error, error: error);
  factory AuthState.resetRequested() =>
      AuthState._(status: AuthStatus.resetRequested, error: null);

  final AuthStatus status;
  final Account? account;
  final String? error;

  AuthState copyWith({AuthStatus? status, Account? account, String? error}) =>
      AuthState._(
        status: status ?? this.status,
        account: account ?? this.account,
        error: error,
      );
}

/// Enumeration of auth status values.
enum AuthStatus {
  initial,
  loading,
  authenticated,
  error,
  resetRequested,
  resetCompleted,
}

/// Provides the [AuthState] via [AuthNotifier] to the widget tree.
final authProvider = StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  final repository = AuthRepository(
    apiClient: ref.read(apiClientProvider),
    secureStore: ref.read(secureStoreProvider),
    sessionNotifier: ref.read(sessionProvider.notifier),
  );
  return AuthNotifier(repository: repository);
});
