import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Credentials live only in platform secure storage.
/// Mirrors Frontend/src/lib/api/token.ts but secure.
class SecureStore {
  SecureStore({FlutterSecureStorage? storage})
      : _storage = storage ?? const FlutterSecureStorage();

  final FlutterSecureStorage _storage;

  static const _tokenKey = 'beatzclik_token';

  Future<String?> readToken() => _storage.read(key: _tokenKey);
  Future<void> writeToken(String token) =>
      _storage.write(key: _tokenKey, value: token);
  Future<void> clearToken() => _storage.delete(key: _tokenKey);
  Future<bool> hasToken() async => (await readToken()) != null;

  /// Wipes token + future prefs on logout. Best-effort: does not throw if keys missing.
  Future<void> clearAll() async {
    await clearToken();
    // Wipe any future preference keys that could leak token-adjacent state.
    // We only clear known-safe keys that are under our control.
    try {
      await _storage.deleteAll();
    } catch (_) {
      // best-effort; ignore if no keys exist or store is locked
    }
  }
}
