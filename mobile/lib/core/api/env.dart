/// Compile-time environment. Mirrors frontend VITE_API_URL convention.
/// Pass via --dart-define=API_ORIGIN=http://127.0.0.1:8080
class AppEnv {
  const AppEnv._();

  static const String apiOrigin = String.fromEnvironment(
    'API_ORIGIN',
    defaultValue: 'https://api.beatzclik.com',
  );

  /// Base for all v1 endpoints.
  static String get apiBase => '$apiOrigin/v1';

  /// Release builds must not use cleartext http.
  static bool get isSecureOrigin => apiOrigin.startsWith('https://');
}
