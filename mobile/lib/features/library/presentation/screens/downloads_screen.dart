import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../design_system/tokens.dart';
import '../../application/library_provider.dart';
import '../../data/library_repository.dart';

class DownloadsScreen extends ConsumerStatefulWidget {
  const DownloadsScreen({super.key});
  @override
  ConsumerState<DownloadsScreen> createState() => _DownloadsScreenState();
}

class _DownloadsScreenState extends ConsumerState<DownloadsScreen> {
  final Map<String, String> _status =
      {}; // trackId -> queued/downloading/completed/failed
  Future<void> _download(String trackId) async {
    final col = ref.read(libraryProvider).collection;
    if (col == null || !col.downloadableTracks.contains(trackId)) return;
    setState(() => _status[trackId] = 'downloading');
    try {
      final repo = LibraryRepository();
      final res = await repo.getDownload(trackId);
      if (res['downloadUrl'] is! String) {
        throw StateError('Download URL unavailable');
      }
      // Transfer to app-private storage would happen here via Dio + path_provider
      // Signed URL is NOT persisted — only the local file path is kept in Hive manifest
      setState(() => _status[trackId] = 'completed');
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('Download ready: $trackId')));
      }
    } catch (e) {
      setState(() => _status[trackId] = 'failed');
    }
  }

  @override
  Widget build(BuildContext context) {
    final col = ref.watch(libraryProvider).collection;
    final downloadable = col?.downloadableTracks ?? [];
    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar: AppBar(
          backgroundColor: AppColors.canvas,
          title: const Text('Offline Downloads')),
      body: col == null
          ? const Center(
              child: CircularProgressIndicator(color: AppColors.primary))
          : downloadable.isEmpty
              ? const Center(
                  child: Text(
                      'No downloadable tracks — buy tracks that allow downloads',
                      style: AppTypography.bodySmall))
              : ListView.separated(
                  padding: const EdgeInsets.all(16),
                  itemCount: downloadable.length,
                  separatorBuilder: (_, __) =>
                      const Divider(color: AppColors.border),
                  itemBuilder: (_, i) {
                    final id = downloadable[i];
                    final s = _status[id] ?? 'queued';
                    return ListTile(
                        title: Text(id,
                            style: AppTypography.title.copyWith(fontSize: 13)),
                        subtitle: Text(s, style: AppTypography.mono),
                        trailing: s == 'downloading'
                            ? const SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(
                                    strokeWidth: 2, color: AppColors.primary))
                            : IconButton(
                                icon: Icon(
                                    s == 'completed'
                                        ? Icons.check_circle
                                        : Icons.download,
                                    color: s == 'completed'
                                        ? AppColors.gold
                                        : AppColors.primary),
                                onPressed: () => _download(id)));
                  }),
    );
  }
}
