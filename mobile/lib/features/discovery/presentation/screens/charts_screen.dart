import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../design_system/tokens.dart';
import '../../../../design_system/widgets/catalog_card.dart';
import '../../application/home_provider.dart';

class ChartsScreen extends ConsumerWidget {
  const ChartsScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final home = ref.watch(homeProvider);
    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar: AppBar(
          backgroundColor: AppColors.canvas, title: const Text('Charts')),
      body: home.when(
        loading: () => const Center(
            child: CircularProgressIndicator(color: AppColors.primary)),
        error: (e, _) => Center(
            child: Text('Failed: $e',
                style: const TextStyle(color: AppColors.error))),
        data: (feed) => ListView(padding: const EdgeInsets.all(16), children: [
          const Text(
              'Derived from trending & top tracks — no invented play counts',
              style: AppTypography.mono,
              textAlign: TextAlign.center),
          const SizedBox(height: 16),
          if (feed.trending.isNotEmpty) ...[
            const Text('Trending Now', style: AppTypography.displayMedium),
            const SizedBox(height: 8),
            ...feed.trending.asMap().entries.map((e) => Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Row(children: [
                  Text('#${e.key + 1}',
                      style: AppTypography.mono.copyWith(
                          color: AppColors.primary,
                          fontWeight: FontWeight.w700)),
                  const SizedBox(width: 12),
                  Expanded(
                      child: CatalogCard(
                          title: e.value.title,
                          subtitle: e.value.artistName,
                          image: e.value.image,
                          ownership: e.value.ownership,
                          price: e.value.price,
                          downloadable: e.value.downloadable))
                ]))),
            const SizedBox(height: 24),
          ],
          if (feed.top10.isNotEmpty) ...[
            const Text('Top 10', style: AppTypography.displayMedium),
            const SizedBox(height: 8),
            ...feed.top10.asMap().entries.map((e) => Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Row(children: [
                  Text('#${e.key + 1}', style: AppTypography.mono),
                  const SizedBox(width: 12),
                  Expanded(
                      child: CatalogCard(
                          title: e.value.title,
                          subtitle: e.value.artistName,
                          image: e.value.image,
                          ownership: e.value.ownership,
                          price: e.value.price,
                          downloadable: e.value.downloadable))
                ]))),
          ],
          if (feed.trending.isEmpty && feed.top10.isEmpty)
            const Center(
                child: Text('No chart data', style: AppTypography.bodySmall)),
        ]),
      ),
    );
  }
}
