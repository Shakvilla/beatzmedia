import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../design_system/tokens.dart';
import '../../../../design_system/widgets/catalog_card.dart';
import '../../application/home_provider.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final home = ref.watch(homeProvider);
    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar:
          AppBar(title: const Text('Home'), backgroundColor: AppColors.canvas),
      body: home.when(
        loading: () => const Center(
            child: CircularProgressIndicator(color: AppColors.primary)),
        error: (e, _) => Center(
            child: Column(mainAxisSize: MainAxisSize.min, children: [
          const Icon(Icons.error_outline, color: AppColors.error),
          const SizedBox(height: 8),
          Text('Failed to load: $e', style: AppTypography.bodySmall),
          const SizedBox(height: 12),
          FilledButton(
              onPressed: () => ref.read(homeProvider.notifier).refresh(),
              child: const Text('Retry'))
        ])),
        data: (feed) => RefreshIndicator(
          onRefresh: () => ref.read(homeProvider.notifier).refresh(),
          child: ListView(padding: const EdgeInsets.all(16), children: [
            if (feed.trending.isNotEmpty) ...[
              const SectionHeader(
                  title: 'Trending', subtitle: 'Now playing across Ghana'),
              const SizedBox(height: 8),
              SizedBox(
                  height: 180,
                  child: ListView.separated(
                      scrollDirection: Axis.horizontal,
                      itemCount: feed.trending.length,
                      separatorBuilder: (_, __) => const SizedBox(width: 12),
                      itemBuilder: (_, i) {
                        final t = feed.trending[i];
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
            if (feed.top10.isNotEmpty) ...[
              const SectionHeader(title: 'Top 10', subtitle: 'Most played'),
              const SizedBox(height: 8),
              SizedBox(
                  height: 180,
                  child: ListView.separated(
                      scrollDirection: Axis.horizontal,
                      itemCount: feed.top10.length,
                      separatorBuilder: (_, __) => const SizedBox(width: 12),
                      itemBuilder: (_, i) {
                        final t = feed.top10[i];
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
            if (feed.featuredAlbums.isNotEmpty) ...[
              const SectionHeader(title: 'Featured Albums'),
              const SizedBox(height: 8),
              SizedBox(
                  height: 180,
                  child: ListView.separated(
                      scrollDirection: Axis.horizontal,
                      itemCount: feed.featuredAlbums.length,
                      separatorBuilder: (_, __) => const SizedBox(width: 12),
                      itemBuilder: (_, i) {
                        final a = feed.featuredAlbums[i];
                        return CatalogCard(
                            title: a.title,
                            subtitle: a.artistName,
                            image: a.coverImage,
                            downloadable: a.downloadable);
                      })),
              const SizedBox(height: 24),
            ],
            if (feed.newReleases.isNotEmpty) ...[
              const SectionHeader(title: 'New Releases'),
              const SizedBox(height: 8),
              SizedBox(
                  height: 180,
                  child: ListView.separated(
                      scrollDirection: Axis.horizontal,
                      itemCount: feed.newReleases.length,
                      separatorBuilder: (_, __) => const SizedBox(width: 12),
                      itemBuilder: (_, i) {
                        final a = feed.newReleases[i];
                        return CatalogCard(
                            title: a.title,
                            subtitle: a.artistName,
                            image: a.coverImage,
                            downloadable: a.downloadable);
                      })),
            ],
            if (feed.trending.isEmpty &&
                feed.top10.isEmpty &&
                feed.featuredAlbums.isEmpty)
              const Center(
                  child: Padding(
                      padding: EdgeInsets.all(24),
                      child: Text('No content yet',
                          style: AppTypography.bodySmall))),
          ]),
        ),
      ),
    );
  }
}
