import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../../design_system/tokens.dart';

class WelcomeScreen extends StatelessWidget {
  const WelcomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.canvas,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Spacer(),
              const Text('Discover.\nOwn.\nPlay.',
                  style: AppTypography.displayLarge),
              const SizedBox(height: 12),
              const Text(
                'Ghanaian and African music — streaming discovery with a buy-to-own marketplace.',
                style: AppTypography.bodySmall,
              ),
              const Spacer(),
              FilledButton(
                onPressed: () => context.go('/signup'),
                child: const Text('Create account'),
              ),
              const SizedBox(height: 10),
              OutlinedButton(
                onPressed: () => context.go('/login'),
                style: OutlinedButton.styleFrom(
                  foregroundColor: AppColors.textPrimary,
                  side: const BorderSide(color: AppColors.border),
                  minimumSize: const Size.fromHeight(48),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(AppRadii.pill)),
                ),
                child: const Text('Sign in',
                    style: TextStyle(fontWeight: FontWeight.w700)),
              ),
              const SizedBox(height: 12),
              const Text(
                'By continuing you agree to our Terms and acknowledge our Privacy Policy.',
                style: AppTypography.monoSmall,
                textAlign: TextAlign.center,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
