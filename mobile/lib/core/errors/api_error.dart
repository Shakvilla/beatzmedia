/// Normalized API error matching Frontend/src/lib/api/errors.ts + API-CONTRACT §1.
/// Wire shape: { error: { code, message, field? } }
class ApiError implements Exception {
  const ApiError({
    required this.status,
    required this.code,
    required this.message,
    this.field,
  });

  final int status;
  final String code;
  final String message;
  final String? field;

  bool get isUnauthorized => status == 401;
  bool get isForbidden => status == 403;
  bool get isNotFound => status == 404;
  bool get isConflict => status == 409;
  bool get isValidation => status == 422;
  bool get isRateLimited => status == 429;

  @override
  String toString() =>
      'ApiError($status $code: $message${field != null ? ' field=$field' : ''})';
}

/// Connectivity / transport failure (no HTTP status).
class NetworkError implements Exception {
  const NetworkError(this.message, {this.cause});
  final String message;
  final Object? cause;

  @override
  String toString() => 'NetworkError: $message';
}
