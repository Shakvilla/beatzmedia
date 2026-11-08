import 'package:flutter/material.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../../../design_system/tokens.dart';
import '../../../../design_system/widgets/catalog_card.dart';
import '../../data/catalog_repository.dart';
import '../../data/dto/catalog_dto.dart';

class ArtistScreen extends StatefulWidget {
  const ArtistScreen({super.key, required this.artistId});
  final String artistId;
  @override
  State<ArtistScreen> createState() => _ArtistScreenState();
}

class _ArtistScreenState extends State<ArtistScreen> {
  ArtistDto? _artist;
  List<TrackDto>? _tracks;
  List<AlbumDto>? _albums;
  List<ShowDto>? _shows;
  bool _loading = true;
  Object? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final repo = CatalogRepository();
    try {
      final artist = await repo.getArtist(widget.artistId);
      final tracks = await repo.getArtistTracks(widget.artistId);
      final albums = await repo.getArtistAlbums(widget.artistId);
      List<ShowDto> shows = [];
      try {
        shows = await repo.getArtistShows(widget.artistId);
      } catch (_) {}
      if (mounted)
        setState(() {
          _artist = artist;
          _tracks = tracks;
          _albums = albums;
          _shows = shows;
          _loading = false;
        });
    } catch (e) {
      if (mounted)
        setState(() {
          _error = e;
          _loading = false;
        });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading)
      return const Scaffold(
          backgroundColor: AppColors.canvas,
          body: Center(
              child: CircularProgressIndicator(color: AppColors.primary)));
    if (_error != null)
      return Scaffold(
          backgroundColor: AppColors.canvas,
          appBar: AppBar(backgroundColor: AppColors.canvas),
          body: Center(
              child: Text('Failed: $_error',
                  style: const TextStyle(color: AppColors.error))));
    final artist = _artist!;
    return Scaffold(
      backgroundColor: AppColors.canvas,
      body: CustomScrollView(slivers: [
        SliverAppBar(
            expandedHeight: 220,
            pinned: true,
            flexibleSpace: FlexibleSpaceBar(
                title: Text(artist.name, style: AppTypography.title),
                background: artist.coverImage != null
                    ? CachedNetworkImage(
                        imageUrl: artist.coverImage!, fit: BoxFit.cover)
                    : Container(color: AppColors.surfaceElevated))),
        SliverPadding(
            padding: const EdgeInsets.all(16),
            sliver: SliverList.list(children: [
              if (artist.bio != null)
                Text(artist.bio!, style: AppTypography.bodySmall),
              const SizedBox(height: 16),
              if (_tracks != null && _tracks!.isNotEmpty) ...[
                const Text('Popular Tracks',
                    style: AppTypography.displayMedium),
                const SizedBox(height: 8),
                SizedBox(
                    height: 180,
                    child: ListView.separated(
                        scrollDirection: Axis.horizontal,
                        itemCount: _tracks!.length,
                        separatorBuilder: (_, __) => const SizedBox(width: 12),
                        itemBuilder: (_, i) {
                          final t = _tracks![i];
                          return CatalogCard(
                              title: t.title,
                              subtitle: t.artistName,
                              image: t.image,
                              ownership: t.ownership,
                              price: t.price,
                              downloadable: t.downloadable);
                        })),
                const SizedBox(height: 24),
              ],
              if (_albums != null && _albums!.isNotEmpty) ...[
                const Text('Discography', style: AppTypography.displayMedium),
                const SizedBox(height: 8),
                SizedBox(
                    height: 180,
                    child: ListView.separated(
                        scrollDirection: Axis.horizontal,
                        itemCount: _albums!.length,
                        separatorBuilder: (_, __) => const SizedBox(width: 12),
                        itemBuilder: (_, i) {
                          final a = _albums![i];
                          return CatalogCard(
                              title: a.title,
                              subtitle: a.artistName,
                              image: a.coverImage,
                              downloadable: a.downloadable);
                        })),
              ],
              if (_shows != null && _shows!.isNotEmpty) ...[
                const SizedBox(height: 24),
                const Text('Upcoming Shows',
                    style: AppTypography.displayMedium),
                const SizedBox(height: 8),
                ..._shows!.map((s) => ListTile(
                    title: Text(s.venue, style: AppTypography.title),
                    subtitle: Text('${s.city} • ${s.date}',
                        style: AppTypography.mono),
                    leading:
                        const Icon(Icons.event, color: AppColors.primary))),
              ],
            ])),
      ]),
    );
  }
}
