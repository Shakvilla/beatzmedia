import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../design_system/tokens.dart';

class OtpVerificationScreen extends ConsumerWidget {
  const OtpVerificationScreen({super.key, required this.email});

  final String email;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      backgroundColor: AppColors.canvas,
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(
                  Icons.email_outlined,
                  size: 64,
                  color: AppColors.primary,
                ),
                const SizedBox(height: 24),
                const Text(
                  'Check your email',
                  style: AppTypography.displayMedium,
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 8),
                Text(
                  'A password reset link has been sent to $email',
                  style: AppTypography.body,
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 24),
                const Text(
                  'Please check your inbox and follow the instructions '
                  'to set a new password.',
                  style: AppTypography.bodySmall,
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 32),
                OutlinedButton(
                  onPressed: () => context.go('/login'),
                  child: const Text('Sign in'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
