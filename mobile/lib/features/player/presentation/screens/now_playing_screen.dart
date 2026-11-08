import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:go_router/go_router.dart';
import '../../../../design_system/tokens.dart';
import '../../application/player_controller.dart';

class NowPlayingScreen extends ConsumerWidget {
  const NowPlayingScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(playerControllerProvider);
    final notifier = ref.read(playerControllerProvider.notifier);
    final track = state.track;
    if (track == null)
      return Scaffold(
          backgroundColor: AppColors.canvas,
          appBar: AppBar(
              backgroundColor: AppColors.canvas,
              title: const Text('Now Playing')),
          body: const Center(
              child: Text('No track', style: AppTypography.bodySmall)));
    String format(Duration d) =>
        '${d.inMinutes}:${(d.inSeconds % 60).toString().padLeft(2, '0')}';
    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar: AppBar(
          backgroundColor: AppColors.canvas,
          title: const Text('Now Playing'),
          actions: [
            IconButton(
                icon: const Icon(Icons.lyrics_outlined),
                onPressed: () => context.push('/lyrics/${track.id}'))
          ]),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(children: [
          ClipRRect(
              borderRadius: BorderRadius.circular(AppRadii.lg),
              child: CachedNetworkImage(
                  imageUrl: track.image,
                  width: 280,
                  height: 280,
                  fit: BoxFit.cover,
                  errorWidget: (_, __, ___) => Container(
                      width: 280,
                      height: 280,
                      color: AppColors.surfaceElevated,
                      child: const Icon(Icons.music_note,
                          size: 64, color: AppColors.textMuted)))),
          const SizedBox(height: 24),
          Text(track.title,
              style: AppTypography.displayMedium.copyWith(fontSize: 22),
              textAlign: TextAlign.center),
          const SizedBox(height: 4),
          Text(track.artistName, style: AppTypography.bodySmall),
          if (state.isPreview)
            Container(
                margin: const EdgeInsets.only(top: 8),
                padding:
                    const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                    color: AppColors.gold,
                    borderRadius: BorderRadius.circular(AppRadii.pill)),
                child: Text(
                    'Preview — ${state.previewSeconds ?? 30}s — Buy to own',
                    style: const TextStyle(
                        color: Colors.black,
                        fontSize: 11,
                        fontWeight: FontWeight.w700))),
          const SizedBox(height: 24),
          Slider(
              value: state.position.inMilliseconds
                  .toDouble()
                  .clamp(0, state.duration.inMilliseconds.toDouble()),
              min: 0,
              max: state.duration.inMilliseconds.toDouble() > 0
                  ? state.duration.inMilliseconds.toDouble()
                  : 1,
              onChanged: (v) =>
                  notifier.seek(Duration(milliseconds: v.toInt())),
              activeColor: AppColors.primary,
              inactiveColor: AppColors.border),
          Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
            Text(format(state.position), style: AppTypography.mono),
            Text(format(state.duration), style: AppTypography.mono)
          ]),
          const SizedBox(height: 16),
          Row(mainAxisAlignment: MainAxisAlignment.center, children: [
            IconButton(
                icon: const Icon(Icons.skip_previous_rounded,
                    size: 36, color: AppColors.textPrimary),
                onPressed: () => notifier.skipPrevious()),
            const SizedBox(width: 12),
            FilledButton(
                onPressed: () => notifier.playPause(),
                style: FilledButton.styleFrom(
                    shape: const CircleBorder(),
                    padding: const EdgeInsets.all(16)),
                child: Icon(
                    state.isPlaying
                        ? Icons.pause_rounded
                        : Icons.play_arrow_rounded,
                    size: 36)),
            const SizedBox(width: 12),
            IconButton(
                icon: const Icon(Icons.skip_next_rounded,
                    size: 36, color: AppColors.textPrimary),
                onPressed: () => notifier.skipNext()),
          ]),
          if (state.error != null)
            Padding(
                padding: const EdgeInsets.only(top: 12),
                child: Text(state.error!,
                    style:
                        const TextStyle(color: AppColors.error, fontSize: 12))),
        ]),
      ),
    );
  }
}
