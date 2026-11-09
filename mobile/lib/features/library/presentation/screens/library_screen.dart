import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../design_system/tokens.dart';
import '../../application/library_provider.dart';

class LibraryScreen extends ConsumerWidget {
  const LibraryScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(libraryProvider);
    final col = state.collection;
    if (state.isLoading && col == null) {
      return const Scaffold(
          backgroundColor: AppColors.canvas,
          body: Center(
              child: CircularProgressIndicator(color: AppColors.primary)));
    }
    if (state.error != null && col == null) {
      return Scaffold(
          backgroundColor: AppColors.canvas,
          appBar: AppBar(
              backgroundColor: AppColors.canvas, title: const Text('Library')),
          body: Center(
              child: Text('Failed: ${state.error}',
                  style: const TextStyle(color: AppColors.error))));
    }
    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar: AppBar(
          backgroundColor: AppColors.canvas, title: const Text('Library')),
      body: RefreshIndicator(
          onRefresh: () => ref.read(libraryProvider.notifier).load(),
          child: ListView(padding: const EdgeInsets.all(16), children: [
            if (col == null)
              const Center(
                  child: Text('Empty library', style: AppTypography.bodySmall))
            else ...[
              _section('Owned Tracks', '${col.ownedTracks.length} tracks',
                  Icons.check_circle, AppColors.gold),
              _section('Liked Tracks', '${col.likedTracks.length} tracks',
                  Icons.favorite, AppColors.primary),
              _section(
                  'Followed Artists',
                  '${col.followedArtists.length} artists',
                  Icons.person,
                  AppColors.textPrimary),
              _section('Saved Albums', '${col.savedAlbums.length} albums',
                  Icons.album, AppColors.textSecondary),
              _section('Playlists', '${col.userPlaylists.length} playlists',
                  Icons.queue_music, AppColors.primary),
              _section(
                  'Downloadable',
                  '${col.downloadableTracks.length} tracks',
                  Icons.download,
                  AppColors.gold),
            ],
          ])),
    );
  }

  Widget _section(String title, String subtitle, IconData icon, Color color) =>
      ListTile(
          leading: Icon(icon, color: color),
          title: Text(title, style: AppTypography.title),
          subtitle: Text(subtitle, style: AppTypography.mono),
          trailing:
              const Icon(Icons.chevron_right, color: AppColors.textMuted));
}
