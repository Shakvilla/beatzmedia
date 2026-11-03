import '../../../core/api/dto/auth_dto.dart';
import '../../../core/api/api_client.dart';
import '../../../core/session/session_provider.dart';
import '../../../core/storage/secure_store.dart';

/// Authentication repository backed by ApiClient.
///
/// All money-related POSTs use no idempotency key (auth writes are not idempotent by design),
/// but never log or render the raw token per 01.5.
class AuthRepository {
  AuthRepository({ApiClient? apiClient, SecureStore? secureStore, SessionNotifier? sessionNotifier})
    : _apiClient = apiClient ?? ApiClient(),
      _secureStore = secureStore ?? SecureStore(),
      _sessionNotifier = sessionNotifier;

  final ApiClient _apiClient;
  final SecureStore _secureStore;
  final SessionNotifier? _sessionNotifier;

  Account? _cachedAccount;

  /// Returns the cached account if available, otherwise attempts a me fetch.
  Future<Account?> getAccount() async {
    if (_cachedAccount != null) return _cachedAccount;
    try {
      final json = await _apiClient.get<Map<String, dynamic>>('/me');
      final dto = AccountDto.fromJson(json);
      _cachedAccount = Account(
        id: dto.id,
        name: dto.name,
        email: dto.email,
        avatar: dto.avatar,
        isArtist: dto.isArtist,
        isAdmin: dto.isAdmin,
      );
      return _cachedAccount;
    } catch (_) {
      return null;
    }
  }

  Future<Account> login(String email, String password) async {
    final resp = await _apiClient.post<Map<String, dynamic>>('/auth/login', data: {
      'email': email,
      'password': password,
    });
    final token = resp['token'] as String;
    final account = _parseAccount(resp['account'] as Map<String, dynamic>);
    if (_sessionNotifier case final session?) {
      await session.setToken(token, account);
    } else {
      await _secureStore.writeToken(token);
      _apiClient.setToken(token);
    }
    _cachedAccount = account;
    return account;
  }

  Future<Account> signup(String name, String email, String password) async {
    final resp = await _apiClient.post<Map<String, dynamic>>('/auth/signup', data: {
      'name': name,
      'email': email,
      'password': password,
    });
    final token = resp['token'] as String;
    final account = _parseAccount(resp['account'] as Map<String, dynamic>);
    if (_sessionNotifier case final session?) {
      await session.setToken(token, account);
    } else {
      await _secureStore.writeToken(token);
      _apiClient.setToken(token);
    }
    _cachedAccount = account;
    return account;
  }

  Future<void> requestPasswordReset(String email) async {
    await _apiClient.post<void>('/me/password/reset', data: {'email': email});
  }

  Future<void> resetPassword(String token, String password) async {
    await _apiClient.post<void>('/auth/password/reset', data: {
      'token': token,
      'password': password,
    });
  }

  Account _parseAccount(Map<String, dynamic> json) {
    final dto = AccountDto.fromJson(json);
    return Account(
      id: dto.id,
      name: dto.name,
      email: dto.email,
      avatar: dto.avatar,
      isArtist: dto.isArtist,
      isAdmin: dto.isAdmin,
    );
  }
}
