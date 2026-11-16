import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../design_system/tokens.dart';
import '../../data/playback_repository.dart';

final lyricsProvider =
    FutureProvider.family<List<dynamic>, String>((ref, trackId) async {
  final repo = PlaybackRepository();
  final res = await repo.getLyrics(trackId);
  return res.lines;
});

class LyricsScreen extends ConsumerWidget {
  const LyricsScreen({super.key, required this.trackId});
  final String trackId;
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(lyricsProvider(trackId));
    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar: AppBar(
          backgroundColor: AppColors.canvas, title: const Text('Lyrics')),
      body: async.when(
        loading: () => const Center(
            child: CircularProgressIndicator(color: AppColors.primary)),
        error: (e, _) => Center(
            child: Text('Failed: $e',
                style: const TextStyle(color: AppColors.error))),
        data: (lines) {
          if (lines.isEmpty) {
            return const Center(
                child: Text('No lyrics available',
                    style: AppTypography.bodySmall));
          }
          return ListView.separated(
              padding: const EdgeInsets.all(24),
              itemCount: lines.length,
              separatorBuilder: (_, __) => const SizedBox(height: 12),
              itemBuilder: (_, i) {
                final l = lines[i] as dynamic;
                return Text(l.text as String? ?? '',
                    style: AppTypography.body.copyWith(height: 1.6));
              });
        },
      ),
    );
  }
}
