import 'package:flutter/material.dart';
import '../../../../design_system/tokens.dart';
import '../../data/dto/podcast_dto.dart';
import '../../data/podcasts_repository.dart';

class PodcastDetailScreen extends StatefulWidget {
  const PodcastDetailScreen({super.key, required this.podcastId});
  final String podcastId;
  @override
  State<PodcastDetailScreen> createState() => _PodcastDetailScreenState();
}

class _PodcastDetailScreenState extends State<PodcastDetailScreen> {
  PodcastDto? _podcast;
  List<PodcastEpisodeDto>? _episodes;
  bool _loading = true;
  Object? _error;
  @override
  void initState() { super.initState(); _load(); }
  Future<void> _load() async { try { final p = await PodcastsRepository().getPodcast(widget.podcastId); final e = await PodcastsRepository().getEpisodes(widget.podcastId); if (mounted) setState(() { _podcast = p; _episodes = e; _loading = false; }); } catch (e) { if (mounted) setState(() { _error = e; _loading = false; }); } }
  @override
  Widget build(BuildContext context) {
    if (_loading) return const Scaffold(backgroundColor: AppColors.canvas, body: Center(child: CircularProgressIndicator(color: AppColors.primary)));
    if (_error != null) return Scaffold(backgroundColor: AppColors.canvas, appBar: AppBar(backgroundColor: AppColors.canvas, title: const Text('Podcast')), body: Center(child: Text('Failed: $_error', style: const TextStyle(color: AppColors.error))));
    final p = _podcast!;
    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar: AppBar(backgroundColor: AppColors.canvas, title: Text(p.title)),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        Row(children: [ClipRRect(borderRadius: BorderRadius.circular(AppRadii.md), child: Image.network(p.image, width: 80, height: 80, fit: BoxFit.cover)), const SizedBox(width: 12), Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(p.title, style: AppTypography.displayMedium.copyWith(fontSize: 16)), Text(p.publisher, style: AppTypography.bodySmall), Text(p.category, style: AppTypography.mono), if (p.seasonPassPrice != null) Text('Season pass GHS ${p.seasonPassPrice!.amount}', style: AppTypography.mono.copyWith(color: AppColors.gold))]))]),
        if (p.description != null) Padding(padding: const EdgeInsets.only(top: 12), child: Text(p.description!, style: AppTypography.bodySmall)),
        const SizedBox(height: 24),
        const Text('Episodes', style: AppTypography.displayMedium),
        const SizedBox(height: 8),
        if (_episodes == null || _episodes!.isEmpty) const Text('No episodes', style: AppTypography.bodySmall) else ..._episodes!.map((e) => ListTile(
              leading: ClipRRect(borderRadius: BorderRadius.circular(6), child: Image.network(e.image, width: 44, height: 44, fit: BoxFit.cover)),
              title: Text(e.title, style: AppTypography.title.copyWith(fontSize: 13)),
              subtitle: Text('${e.showTitle} • ${e.duration ~/ 60}m • ${e.isPremium == true ? (e.isOwned == true ? 'OWNED' : 'GHS ${e.price?.amount ?? 0}') : 'FREE'}${e.isEarlyAccess == true ? ' • Early access until ${e.publicAt}' : ''}', style: AppTypography.mono),
              trailing: e.isPremium == true && e.isOwned != true ? const Icon(Icons.lock, size: 16, color: AppColors.gold) : const Icon(Icons.play_arrow, size: 16, color: AppColors.primary),
            )),
      ]),
    );
  }
}
