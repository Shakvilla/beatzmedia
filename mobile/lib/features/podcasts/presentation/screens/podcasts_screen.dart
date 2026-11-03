import 'package:flutter/material.dart';
import '../../../../design_system/tokens.dart';
import '../../data/podcasts_repository.dart';
import '../../data/dto/podcast_dto.dart';

class PodcastsScreen extends StatefulWidget {
  const PodcastsScreen({super.key});
  @override
  State<PodcastsScreen> createState() => _PodcastsScreenState();
}

class _PodcastsScreenState extends State<PodcastsScreen> {
  List<PodcastDto>? _podcasts;
  bool _loading = true;
  Object? _error;
  @override
  void initState() { super.initState(); _load(); }
  Future<void> _load() async { try { final list = await PodcastsRepository().getPodcasts(); if (mounted) setState(() { _podcasts = list; _loading = false; }); } catch (e) { if (mounted) setState(() { _error = e; _loading = false; }); } }
  @override
  Widget build(BuildContext context) {
    if (_loading) return const Scaffold(backgroundColor: AppColors.canvas, body: Center(child: CircularProgressIndicator(color: AppColors.primary)));
    if (_error != null) return Scaffold(backgroundColor: AppColors.canvas, appBar: AppBar(backgroundColor: AppColors.canvas, title: const Text('Podcasts')), body: Center(child: Text('Failed: $_error', style: const TextStyle(color: AppColors.error))));
    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar: AppBar(backgroundColor: AppColors.canvas, title: const Text('Podcasts')),
      body: _podcasts!.isEmpty ? const Center(child: Text('No podcasts', style: AppTypography.bodySmall)) : ListView.separated(padding: const EdgeInsets.all(16), itemCount: _podcasts!.length, separatorBuilder: (_, __) => const SizedBox(height: 12), itemBuilder: (_, i) { final p = _podcasts![i]; return ListTile(leading: ClipRRect(borderRadius: BorderRadius.circular(8), child: Image.network(p.image, width: 56, height: 56, fit: BoxFit.cover)), title: Text(p.title, style: AppTypography.title.copyWith(fontSize: 14)), subtitle: Text('${p.publisher} • ${p.category}', style: AppTypography.mono), trailing: p.supportsTips == true ? const Icon(Icons.volunteer_activism, size: 16, color: AppColors.gold) : null, onTap: () {}); }),
    );
  }
}
