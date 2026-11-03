class NotificationDto {
  const NotificationDto({required this.id, required this.type, required this.title, required this.body, required this.time, required this.read, this.to});
  final String id; final String type; final String title; final String body; final String time; final bool read; final String? to;
  factory NotificationDto.fromJson(Map<String, dynamic> j) => NotificationDto(id: j['id'] as String, type: j['type'] as String, title: j['title'] as String, body: j['body'] as String, time: j['time'] as String, read: j['read'] as bool, to: j['to'] as String?);
}
