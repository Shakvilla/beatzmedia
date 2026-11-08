import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../design_system/tokens.dart';
import '../../../../design_system/widgets/catalog_card.dart';
import '../../application/search_provider.dart';
import '../../data/catalog_repository.dart';
import '../../data/dto/catalog_dto.dart';

class SearchScreen extends ConsumerStatefulWidget {
  const SearchScreen({super.key});
  @override
  ConsumerState<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends ConsumerState<SearchScreen> {
  final _controller = TextEditingController();
  List<BrowseCategoryDto>? _categories;
  bool _loadingCategories = true;

  @override
  void initState() {
    super.initState();
    _loadCategories();
  }

  Future<void> _loadCategories() async {
    try {
      final cats = await CatalogRepository().getBrowseCategories();
      if (mounted) {
        setState(() {
          _categories = cats;
          _loadingCategories = false;
        });
      }
    } catch (_) {
      if (mounted) setState(() => _loadingCategories = false);
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final searchState = ref.watch(searchProvider);
    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar: AppBar(
          backgroundColor: AppColors.canvas, title: const Text('Search')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child:
            Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          TextField(
            controller: _controller,
            decoration: InputDecoration(
                hintText: 'Search tracks, artists, albums',
                prefixIcon:
                    const Icon(Icons.search, color: AppColors.textMuted),
                border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(AppRadii.md)),
                filled: true,
                fillColor: AppColors.surfaceElevated),
            onChanged: (v) => ref.read(searchProvider.notifier).search(v),
            onSubmitted: (v) => ref.read(searchProvider.notifier).search(v),
          ),
          const SizedBox(height: 16),
          if (searchState.query.isEmpty) ...[
            if (_loadingCategories)
              const Center(
                  child: CircularProgressIndicator(color: AppColors.primary))
            else if (_categories != null && _categories!.isNotEmpty)
              Expanded(
                  child: GridView.builder(
                      gridDelegate:
                          const SliverGridDelegateWithFixedCrossAxisCount(
                              crossAxisCount: 2,
                              childAspectRatio: 2.8,
                              crossAxisSpacing: 12,
                              mainAxisSpacing: 12),
                      itemCount: _categories!.length,
                      itemBuilder: (_, i) {
                        final c = _categories![i];
                        return Container(
                            decoration: BoxDecoration(
                                color: AppColors.surfaceElevated,
                                borderRadius:
                                    BorderRadius.circular(AppRadii.md)),
                            padding: const EdgeInsets.all(14),
                            child: Text(c.title,
                                style: AppTypography.title
                                    .copyWith(fontSize: 14)));
                      }))
            else
              const Text('Type at least 2 characters to search',
                  style: AppTypography.bodySmall),
          ] else ...[
            if (searchState.isSearching)
              const LinearProgressIndicator(color: AppColors.primary),
            if (searchState.error != null)
              Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: Text('Search failed: ${searchState.error}',
                      style: const TextStyle(
                          color: AppColors.error, fontSize: 12))),
            Expanded(
                child: SingleChildScrollView(
                    child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                  if (searchState.result != null) ...[
                    if (searchState.result!.tracks.isNotEmpty) ...[
                      _section(
                          'Tracks',
                          searchState.result!.tracks
                              .map((t) => CatalogCard(
                                  title: t.title,
                                  subtitle: t.artistName,
                                  image: t.image,
                                  ownership: t.ownership,
                                  price: t.price,
                                  downloadable: t.downloadable))
                              .toList())
                    ],
                    if (searchState.result!.artists.isNotEmpty) ...[
                      _section(
                          'Artists',
                          searchState.result!.artists
                              .map((a) => CatalogCard(
                                  title: a.name,
                                  subtitle: a.location ?? '',
                                  image: a.image))
                              .toList())
                    ],
                    if (searchState.result!.albums.isNotEmpty) ...[
                      _section(
                          'Albums',
                          searchState.result!.albums
                              .map((a) => CatalogCard(
                                  title: a.title,
                                  subtitle: a.artistName,
                                  image: a.coverImage,
                                  downloadable: a.downloadable))
                              .toList())
                    ],
                    if (searchState.result!.tracks.isEmpty &&
                        searchState.result!.artists.isEmpty &&
                        searchState.result!.albums.isEmpty)
                      const Padding(
                          padding: EdgeInsets.all(24),
                          child: Text('No results',
                              style: AppTypography.bodySmall)),
                  ],
                ]))),
          ],
        ]),
      ),
    );
  }

  Widget _section(String title, List<Widget> cards) =>
      Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(title, style: AppTypography.displayMedium.copyWith(fontSize: 16)),
        const SizedBox(height: 8),
        SizedBox(
            height: 180,
            child: ListView.separated(
                scrollDirection: Axis.horizontal,
                itemCount: cards.length,
                separatorBuilder: (_, __) => const SizedBox(width: 12),
                itemBuilder: (_, i) => cards[i])),
        const SizedBox(height: 16)
      ]);
}
