import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../api/api_client.dart';
import '../api/dto/account_dto.dart';
import '../errors/api_error.dart';
import '../storage/secure_store.dart';

/// Minimal account model from GET /v1/me.
class Account {
  const Account({
    required this.id,
    required this.name,
    required this.email,
    this.avatar,
    this.isArtist = false,
    this.isAdmin = false,
  });
  final String id;
  final String name;
  final String email;
  final String? avatar;
  final bool isArtist;
  final bool isAdmin;
}

enum SessionStatus { initial, restoring, authenticated, unauthenticated }

class SessionState {
  const SessionState._({required this.status, this.account});
  factory SessionState.initial() =>
      const SessionState._(status: SessionStatus.initial);
  factory SessionState.restoring() =>
      const SessionState._(status: SessionStatus.restoring);
  factory SessionState.authenticated(Account account) =>
      SessionState._(status: SessionStatus.authenticated, account: account);
  factory SessionState.unauthenticated() =>
      const SessionState._(status: SessionStatus.unauthenticated);

  final SessionStatus status;
  final Account? account;
}

final apiClientProvider = Provider<ApiClient>((ref) => ApiClient());
final secureStoreProvider = Provider<SecureStore>((ref) => SecureStore());

class SessionNotifier extends StateNotifier<SessionState> {
  SessionNotifier(
      {required ApiClient apiClient, required SecureStore secureStore})
      : _apiClient = apiClient,
        _secureStore = secureStore,
        super(SessionState.initial());

  final ApiClient _apiClient;
  final SecureStore _secureStore;

  Future<void> restore() async {
    state = SessionState.restoring();
    final token = await _secureStore.readToken();
    if (token == null || token.isEmpty) {
      await _clearToUnauthenticated();
      return;
    }
    _apiClient.setToken(token);
    try {
      final json = await _apiClient.get<Map<String, dynamic>>('/me');
      final dto = AccountDto.fromJson(json);
      final account = Account(
        id: dto.id,
        name: dto.name,
        email: dto.email,
        avatar: dto.avatar,
        isArtist: dto.isArtist,
        isAdmin: dto.isAdmin,
      );
      state = SessionState.authenticated(account);
    } on ApiError catch (e) {
      if (e.isUnauthorized) {
        await _clearToUnauthenticated();
      } else {
        await _secureStore.clearToken();
        _apiClient.clearToken();
        state = SessionState.unauthenticated();
      }
    } catch (_) {
      await _secureStore.clearToken();
      _apiClient.clearToken();
      state = SessionState.unauthenticated();
    }
  }

  Future<void> logout() async {
    try {
      await _apiClient.post<void>('/auth/logout');
    } catch (_) {}
    await _secureStore.clearAll();
    _apiClient.clearToken();
    state = SessionState.unauthenticated();
  }

  Future<void> setToken(String token, Account account) async {
    await _secureStore.writeToken(token);
    _apiClient.setToken(token);
    state = SessionState.authenticated(account);
  }

  Future<void> _clearToUnauthenticated() async {
    await _secureStore.clearToken();
    _apiClient.clearToken();
    state = SessionState.unauthenticated();
  }
}

final sessionProvider =
    StateNotifierProvider<SessionNotifier, SessionState>((ref) {
  final apiClient = ref.watch(apiClientProvider);
  final secureStore = ref.watch(secureStoreProvider);
  return SessionNotifier(apiClient: apiClient, secureStore: secureStore);
});

final isAuthenticatedProvider = Provider<bool>((ref) {
  return ref.watch(
      sessionProvider.select((s) => s.status == SessionStatus.authenticated));
});
