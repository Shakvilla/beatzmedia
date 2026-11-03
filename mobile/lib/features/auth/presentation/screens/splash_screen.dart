import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/session/session_provider.dart';
import '../../../../design_system/tokens.dart';

class SplashScreen extends ConsumerStatefulWidget {
  const SplashScreen({super.key});

  @override
  ConsumerState<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends ConsumerState<SplashScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(sessionProvider.notifier).restore();
    });
  }

  @override
  Widget build(BuildContext context) {
    ref.listen<SessionState>(sessionProvider, (prev, next) async {
      if (next.status == SessionStatus.authenticated) {
        try {
          final preferences = await ref.read(apiClientProvider).get<Map<String, dynamic>>('/me/preferences');
          if (!context.mounted) return;
          context.go(preferences['onboarded'] == true ? '/home' : '/onboarding');
        } catch (_) {
          if (context.mounted) context.go('/home');
        }
      } else if (next.status == SessionStatus.unauthenticated) {
        context.go('/welcome');
      }
    });

    return Scaffold(
      backgroundColor: AppColors.canvas,
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 88,
              height: 88,
              decoration: BoxDecoration(
                color: AppColors.primary,
                borderRadius: BorderRadius.circular(28),
              ),
              child: const Icon(Icons.play_arrow_rounded, size: 48, color: Colors.white),
            ),
            const SizedBox(height: 16),
            const Text('BeatzClik', style: AppTypography.displayMedium),
            const SizedBox(height: 6),
            const Text('Ghana · Africa · Worldwide', style: AppTypography.mono),
            const SizedBox(height: 24),
            const SizedBox(
              width: 24,
              height: 24,
              child: CircularProgressIndicator(strokeWidth: 2, color: AppColors.textMuted),
            ),
          ],
        ),
      ),
    );
  }
}
