import 'package:flutter/material.dart';
import '../../../../design_system/tokens.dart';

class FriendActivityScreen extends StatelessWidget {
  const FriendActivityScreen({super.key});
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar: AppBar(
          backgroundColor: AppColors.canvas,
          title: const Text('Friend Activity')),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Container(
                width: 80,
                height: 80,
                decoration: BoxDecoration(
                    color: AppColors.surfaceElevated,
                    borderRadius: BorderRadius.circular(20)),
                child: const Icon(Icons.people_outline,
                    size: 40, color: AppColors.textMuted)),
            const SizedBox(height: 16),
            const Text('Coming soon', style: AppTypography.displayMedium),
            const SizedBox(height: 8),
            const Text(
                'Friend activity is not yet available. We will show listening activity from people you follow — no fabricated people or listening data here.',
                style: AppTypography.bodySmall,
                textAlign: TextAlign.center),
            const SizedBox(height: 16),
            FilledButton(
                onPressed: () => Navigator.of(context).pop(),
                child: const Text('Back to Home')),
          ]),
        ),
      ),
    );
  }
}
