import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'session_provider.dart';

/// Handles a 401 by clearing session and navigating to Welcome.
/// Single coordinated path — prevents thundering herd on concurrent 401s.
Future<void> handleAuthExpiry(BuildContext context, WidgetRef ref) async {
  final notifier = ref.read(sessionProvider.notifier);
  await notifier.logout();
  if (!context.mounted) return;
  context.go('/welcome');
}

/// Non-widget helper for Dio interceptor (no BuildContext).
Future<void> handleAuthExpiryForDio(WidgetRef ref) async {
  await ref.read(sessionProvider.notifier).logout();
}
