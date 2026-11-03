import 'package:flutter/material.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../../../design_system/tokens.dart';
import '../../../../design_system/widgets/catalog_card.dart';
import '../../data/catalog_repository.dart';
import '../../data/dto/catalog_dto.dart';

class AlbumScreen extends StatefulWidget {
  const AlbumScreen({super.key, required this.albumId});
  final String albumId;
  @override
  State<AlbumScreen> createState() => _AlbumScreenState();
}

class _AlbumScreenState extends State<AlbumScreen> {
  AlbumDto? _album;
  List<TrackDto>? _tracks;
  bool _loading = true;
  Object? _error;

  @override
  void initState() { super.initState(); _load(); }

  Future<void> _load() async {
    final repo = CatalogRepository();
    try {
      final raw = await repo.getAlbumRaw(widget.albumId);
      final album = AlbumDto.fromJson(raw);
      final tracks = raw['tracks'] != null ? (raw['tracks'] as List).map((e) => TrackDto.fromJson(e as Map<String, dynamic>)).toList() : <TrackDto>[];
      if (mounted) setState(() { _album = album; _tracks = tracks; _loading = false; });
    } catch (e) {
      if (mounted) setState(() { _error = e; _loading = false; });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) return const Scaffold(backgroundColor: AppColors.canvas, body: Center(child: CircularProgressIndicator(color: AppColors.primary)));
    if (_error != null) return Scaffold(backgroundColor: AppColors.canvas, appBar: AppBar(backgroundColor: AppColors.canvas), body: Center(child: Text('Failed: $_error', style: const TextStyle(color: AppColors.error))));
    final album = _album!;
    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar: AppBar(backgroundColor: AppColors.canvas, title: Text(album.title)),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          ClipRRect(borderRadius: BorderRadius.circular(AppRadii.md), child: CachedNetworkImage(imageUrl: album.coverImage, width: 140, height: 140, fit: BoxFit.cover)),
          const SizedBox(width: 16),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(album.title, style: AppTypography.displayMedium.copyWith(fontSize: 18)),
            const SizedBox(height: 4),
            Text(album.artistName, style: AppTypography.bodySmall),
            const SizedBox(height: 4),
            Text('${album.year} • ${album.trackIds.length} tracks', style: AppTypography.mono),
            const SizedBox(height: 8),
            Row(children: [Icon(album.downloadable ? Icons.download_done_rounded : Icons.block, size: 14, color: album.downloadable ? AppColors.gold : AppColors.textMuted), const SizedBox(width: 4), Text(album.downloadable ? 'Downloadable' : 'Streaming only', style: AppTypography.mono)]),
          ])),
        ]),
        const SizedBox(height: 24),
        if (_tracks != null && _tracks!.isNotEmpty) ...[
          const Text('Tracks', style: AppTypography.displayMedium),
          const SizedBox(height: 8),
          ..._tracks!.asMap().entries.map((e) => ListTile(
                leading: Text('${e.key + 1}', style: AppTypography.mono),
                title: Text(e.value.title, style: AppTypography.title.copyWith(fontSize: 14)),
                subtitle: Text('${e.value.artistName} • ${e.value.duration ~/ 60}:${(e.value.duration % 60).toString().padLeft(2, '0')} • ${e.value.ownership}${e.value.price != null ? ' • GHS ${e.value.price!.amount}' : ''}', style: AppTypography.mono),
                trailing: e.value.downloadable ? const Icon(Icons.download, size: 16, color: AppColors.gold) : null,
              )),
        ] else ...[
          const Text('No tracks', style: AppTypography.bodySmall),
        ],
      ]),
    );
  }
}
