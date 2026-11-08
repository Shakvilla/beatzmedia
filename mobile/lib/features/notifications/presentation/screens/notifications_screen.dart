import 'package:flutter/material.dart';
import '../../../../design_system/tokens.dart';
import '../../data/dto/notification_dto.dart';
import '../../data/notifications_repository.dart';
import 'package:go_router/go_router.dart';

class NotificationsScreen extends StatefulWidget {
  const NotificationsScreen({super.key});
  @override
  State<NotificationsScreen> createState() => _NotificationsScreenState();
}

class _NotificationsScreenState extends State<NotificationsScreen> {
  List<NotificationDto>? _items;
  bool _loading = true;
  Object? _error;
  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final list = await NotificationsRepository().getNotifications();
      if (mounted) {
        setState(() {
          _items = list;
          _loading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e;
          _loading = false;
        });
      }
    }
  }

  Future<void> _markAll() async {
    await NotificationsRepository().markAllRead();
    _load();
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Scaffold(
          backgroundColor: AppColors.canvas,
          body: Center(
              child: CircularProgressIndicator(color: AppColors.primary)));
    }
    if (_error != null) {
      return Scaffold(
          backgroundColor: AppColors.canvas,
          appBar: AppBar(
              backgroundColor: AppColors.canvas,
              title: const Text('Notifications')),
          body: Center(
              child: Text('Failed: $_error',
                  style: const TextStyle(color: AppColors.error))));
    }
    final items = _items!;
    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar: AppBar(
          backgroundColor: AppColors.canvas,
          title: const Text('Notifications'),
          actions: [
            TextButton(
                onPressed: _markAll,
                child: const Text('Mark all read',
                    style: TextStyle(color: AppColors.primary)))
          ]),
      body: items.isEmpty
          ? const Center(
              child: Text('No notifications', style: AppTypography.bodySmall))
          : ListView.separated(
              padding: const EdgeInsets.all(16),
              itemCount: items.length,
              separatorBuilder: (_, __) =>
                  const Divider(color: AppColors.border),
              itemBuilder: (_, i) {
                final n = items[i];
                return ListTile(
                    leading: Icon(
                        n.read ? Icons.notifications_none : Icons.notifications,
                        color:
                            n.read ? AppColors.textMuted : AppColors.primary),
                    title: Text(n.title,
                        style: AppTypography.title.copyWith(fontSize: 13)),
                    subtitle: Text(n.body, style: AppTypography.bodySmall),
                    trailing: Text(n.time, style: AppTypography.mono),
                    onTap: () async {
                      if (!n.read) {
                        await NotificationsRepository().markOneRead(n.id);
                      }
                      if (n.to != null &&
                          n.to!.startsWith('/') &&
                          !n.to!.contains('://')) {
                        if (context.mounted) context.push(n.to!);
                      }
                    });
              }),
    );
  }
}
