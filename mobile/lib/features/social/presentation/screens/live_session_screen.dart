import 'package:flutter/material.dart';
import '../../../../design_system/tokens.dart';

class LiveSessionScreen extends StatelessWidget {
  const LiveSessionScreen({super.key});
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar: AppBar(backgroundColor: AppColors.canvas, title: const Text('Live Session')),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Container(width: 80, height: 80, decoration: BoxDecoration(color: AppColors.surfaceElevated, borderRadius: BorderRadius.circular(20)), child: const Icon(Icons.live_tv_outlined, size: 40, color: AppColors.textMuted)),
            const SizedBox(height: 16),
            const Text('Coming soon', style: AppTypography.displayMedium),
            const SizedBox(height: 8),
            const Text('Live sessions are not yet available. No fake join, chat, or payment actions are shown here.', style: AppTypography.bodySmall, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            FilledButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Back to Home')),
          ]),
        ),
      ),
    );
  }
}
