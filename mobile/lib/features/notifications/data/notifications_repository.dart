import '../../../../core/api/api_client.dart';
import 'dto/notification_dto.dart';

class NotificationsRepository {
  NotificationsRepository({ApiClient? apiClient})
      : _api = apiClient ?? ApiClient();
  final ApiClient _api;
  Future<List<NotificationDto>> getNotifications() async {
    final json = await _api.get<Map<String, dynamic>>('/me/notifications');
    final items =
        (json['items'] as List?) ?? (json['notifications'] as List?) ?? [];
    return items
        .map((e) => NotificationDto.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<void> markOneRead(String id) async =>
      _api.post<void>('/me/notifications/$id/read');
  Future<void> markAllRead() async => _api.post<void>('/me/notifications/read');
}
