import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../../../design_system/tokens.dart';
import '../../application/player_controller.dart';

class CompactPlayer extends ConsumerWidget {
  const CompactPlayer({super.key, this.onTap});
  final VoidCallback? onTap;
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(playerControllerProvider);
    final track = state.track;
    if (track == null) return const SizedBox.shrink();
    return GestureDetector(
      onTap: onTap,
      child: Container(
        height: 64,
        decoration: const BoxDecoration(color: AppColors.surfaceElevated, border: Border(top: BorderSide(color: AppColors.border))),
        padding: const EdgeInsets.symmetric(horizontal: 12),
        child: Row(children: [
          ClipRRect(borderRadius: BorderRadius.circular(6), child: CachedNetworkImage(imageUrl: track.image, width: 44, height: 44, fit: BoxFit.cover, errorWidget: (_, __, ___) => Container(width: 44, height: 44, color: AppColors.surface, child: const Icon(Icons.music_note, size: 20, color: AppColors.textMuted)))),
          const SizedBox(width: 10),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisAlignment: MainAxisAlignment.center, children: [
            Text(track.title, style: AppTypography.title.copyWith(fontSize: 13), maxLines: 1, overflow: TextOverflow.ellipsis),
            Text(track.artistName, style: AppTypography.mono, maxLines: 1),
            if (state.isPreview) const Text('Preview — 30s — Buy to own', style: TextStyle(fontFamily: AppTypography.monoFamily, fontSize: 10, color: AppColors.gold)),
            LinearProgressIndicator(value: state.duration.inMilliseconds > 0 ? state.position.inMilliseconds / state.duration.inMilliseconds : 0, color: AppColors.primary, backgroundColor: AppColors.border, minHeight: 2),
          ])),
          IconButton(icon: Icon(state.isPlaying ? Icons.pause_rounded : Icons.play_arrow_rounded, color: AppColors.textPrimary), onPressed: () => ref.read(playerControllerProvider.notifier).playPause()),
          IconButton(icon: const Icon(Icons.close_rounded, size: 18, color: AppColors.textMuted), onPressed: () {}),
        ]),
      ),
    );
  }
}
