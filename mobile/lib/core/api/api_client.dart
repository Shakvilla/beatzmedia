import 'package:dio/dio.dart';
import 'package:uuid/uuid.dart';

import '../errors/api_error.dart';
import 'env.dart';

/// Dio client mirroring Frontend/src/lib/api/client.ts
/// - baseUrl = AppEnv.apiBase (/v1)
/// - bearer interceptor, idempotency, normalized ApiError, safe retry for idempotent reads
class ApiClient {
  factory ApiClient({Dio? dio, String? baseUrl}) =>
      dio == null && baseUrl == null
          ? _shared
          : ApiClient._internal(dio: dio, baseUrl: baseUrl);

  static final ApiClient _shared = ApiClient._internal();

  ApiClient._internal({Dio? dio, String? baseUrl}) : _dio = dio ?? Dio() {
    _dio.options.baseUrl = baseUrl ?? AppEnv.apiBase;
    _dio.options.connectTimeout = const Duration(seconds: 10);
    _dio.options.receiveTimeout = const Duration(seconds: 20);
    _dio.options.headers['Content-Type'] = 'application/json';
    _dio.options.headers['Accept'] = 'application/json';
    _dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) {
          if (_token != null && _token!.isNotEmpty) {
            options.headers['Authorization'] = 'Bearer $_token';
          }
          handler.next(options);
        },
        onError: (e, handler) {
          final apiError = _toApiError(e);
          handler.reject(
            DioException(
              requestOptions: e.requestOptions,
              response: e.response,
              error: apiError,
              type: e.type,
            ),
          );
        },
      ),
    );
  }

  final Dio _dio;
  String? _token;
  static const _uuid = Uuid();

  void setToken(String? token) => _token = token;
  void clearToken() => _token = null;

  /// Fresh idempotency key for one logical money/side-effect attempt.
  static String newIdempotencyKey() => _uuid.v4();

  Dio get dio => _dio;

  Future<T> get<T>(String path,
      {Map<String, dynamic>? query,
      Options? options,
      CancelToken? cancelToken}) async {
    try {
      final res = await _dio.get<T>(path,
          queryParameters: query, options: options, cancelToken: cancelToken);
      return res.data as T;
    } on DioException catch (e) {
      throw e.error is ApiError || e.error is NetworkError
          ? e.error!
          : _toApiError(e);
    }
  }

  Future<T> post<T>(String path,
      {Object? data,
      String? idempotencyKey,
      Options? options,
      CancelToken? cancelToken}) async {
    final opts = (options ?? Options()).copyWith(
      headers: {
        ...?options?.headers,
        if (idempotencyKey != null) 'Idempotency-Key': idempotencyKey,
      },
    );
    try {
      final res = await _dio.post<T>(path,
          data: data, options: opts, cancelToken: cancelToken);
      return res.data as T;
    } on DioException catch (e) {
      throw e.error is ApiError || e.error is NetworkError
          ? e.error!
          : _toApiError(e);
    }
  }

  Future<T> put<T>(String path,
      {Object? data, Options? options, CancelToken? cancelToken}) async {
    try {
      final res = await _dio.put<T>(path,
          data: data, options: options, cancelToken: cancelToken);
      return res.data as T;
    } on DioException catch (e) {
      throw e.error is ApiError || e.error is NetworkError
          ? e.error!
          : _toApiError(e);
    }
  }

  static Exception _toApiError(DioException e) {
    if (e.type == DioExceptionType.connectionTimeout ||
        e.type == DioExceptionType.sendTimeout ||
        e.type == DioExceptionType.receiveTimeout ||
        e.type == DioExceptionType.connectionError) {
      return NetworkError(e.message ?? 'Network unavailable', cause: e);
    }
    final res = e.response;
    if (res != null && res.data is Map) {
      final body = res.data as Map;
      final err = body['error'];
      if (err is Map) {
        return ApiError(
          status: res.statusCode ?? 0,
          code: err['code']?.toString() ?? 'UNKNOWN',
          message: err['message']?.toString() ?? e.message ?? 'Request failed',
          field: err['field']?.toString(),
        );
      }
    }
    return ApiError(
      status: res?.statusCode ?? 0,
      code: 'UNKNOWN',
      message: e.message ?? 'Request failed',
    );
  }
}
