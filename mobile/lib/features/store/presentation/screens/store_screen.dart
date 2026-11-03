import 'package:flutter/material.dart';
import '../../../../design_system/tokens.dart';
import '../../data/dto/store_dto.dart';
import '../../../podcasts/data/podcasts_repository.dart';

class StoreScreen extends StatefulWidget {
  const StoreScreen({super.key});
  @override
  State<StoreScreen> createState() => _StoreScreenState();
}

class _StoreScreenState extends State<StoreScreen> with SingleTickerProviderStateMixin {
  late TabController _tab;
  @override
  void initState() { super.initState(); _tab = TabController(length: 4, vsync: this); }
  @override
  void dispose() { _tab.dispose(); super.dispose(); }
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar: AppBar(backgroundColor: AppColors.canvas, title: const Text('Store'), bottom: TabBar(controller: _tab, labelColor: AppColors.primary, unselectedLabelColor: AppColors.textMuted, indicatorColor: AppColors.primary, tabs: const [Tab(text: 'All'), Tab(text: 'Premium'), Tab(text: 'Pre-orders'), Tab(text: 'Beats')])),
      body: TabBarView(controller: _tab, children: [
        _StoreList(type: null),
        _StoreList(type: 'EXCLUSIVE'),
        _StoreList(type: 'PREORDER'),
        _StoreList(type: 'BEAT_LICENSE'),
      ]),
    );
  }
}

class _StoreList extends StatefulWidget {
  const _StoreList({this.type});
  final String? type;
  @override
  State<_StoreList> createState() => _StoreListState();
}

class _StoreListState extends State<_StoreList> {
  List<StoreItemDto>? _items;
  bool _loading = true;
  Object? _error;
  @override
  void initState() { super.initState(); _load(); }
  Future<void> _load() async { try { final list = await StoreRepository().getStore(type: widget.type == 'PREORDER' ? null : widget.type, sort: 'popular'); if (mounted) setState(() { _items = widget.type == 'PREORDER' ? list.where((e) => e.dropsAt != null).toList() : list; _loading = false; }); } catch (e) { if (mounted) setState(() { _error = e; _loading = false; }); } }
  @override
  Widget build(BuildContext context) {
    if (_loading) return const Center(child: CircularProgressIndicator(color: AppColors.primary));
    if (_error != null) return Center(child: Text('Failed: $_error', style: const TextStyle(color: AppColors.error)));
    final items = _items!;
    if (items.isEmpty) return Center(child: Text(widget.type == 'PREORDER' ? 'No pre-orders — presentation only, no reservation action unless store data supports it' : 'No items', style: AppTypography.bodySmall));
    return ListView.separated(padding: const EdgeInsets.all(16), itemCount: items.length, separatorBuilder: (_, __) => const SizedBox(height: 12), itemBuilder: (_, i) { final it = items[i]; return ListTile(leading: ClipRRect(borderRadius: BorderRadius.circular(8), child: Image.network(it.image, width: 56, height: 56, fit: BoxFit.cover)), title: Text(it.title, style: AppTypography.title.copyWith(fontSize: 14)), subtitle: Text('${it.artistName} • GHS ${it.price.amount} • ${it.type}', style: AppTypography.mono), trailing: it.stockRemaining != null ? Text('${it.stockRemaining} left', style: AppTypography.mono.copyWith(color: AppColors.gold)) : null, onTap: () {}); });
  }
}

class _StoreDetail extends StatelessWidget {
  const _StoreDetail({required this.item});
  final StoreItemDto item;
  @override
  Widget build(BuildContext context) {
    return Scaffold(backgroundColor: AppColors.canvas, appBar: AppBar(backgroundColor: AppColors.canvas, title: Text(item.title)), body: ListView(padding: const EdgeInsets.all(16), children: [
      ClipRRect(borderRadius: BorderRadius.circular(AppRadii.md), child: Image.network(item.image, height: 200, fit: BoxFit.cover)),
      const SizedBox(height: 16),
      Text(item.title, style: AppTypography.displayMedium),
      Text(item.artistName, style: AppTypography.bodySmall),
      const SizedBox(height: 8),
      Text('GHS ${item.price.amount} • ${item.type}', style: AppTypography.mono.copyWith(color: AppColors.primary)),
      if (item.description != null) Padding(padding: const EdgeInsets.only(top: 12), child: Text(item.description!, style: AppTypography.bodySmall)),
      if (item.licenseOptions != null) ...[const SizedBox(height: 16), const Text('License options', style: AppTypography.title), ...item.licenseOptions!.map((o) => ListTile(title: Text(o.label, style: AppTypography.title.copyWith(fontSize: 13)), subtitle: Text(o.features.join(' • '), style: AppTypography.mono), trailing: Text('GHS ${o.price.amount}', style: AppTypography.mono.copyWith(color: AppColors.primary))))],
      if (item.variants != null) ...[const SizedBox(height: 16), ...item.variants!.map((v) => Text('${v.label}: ${v.options.join(', ')}', style: AppTypography.bodySmall))],
      const SizedBox(height: 24),
      FilledButton(onPressed: item.variants != null && item.variants!.isNotEmpty ? null : () {}, child: Text(item.variants != null && item.variants!.isNotEmpty ? 'Select options first' : 'Add to cart')),
      if (item.variants != null && item.variants!.isNotEmpty) const Padding(padding: EdgeInsets.only(top: 8), child: Text('Beat-license/merch variants require explicit selection before adding to cart', style: AppTypography.monoSmall, textAlign: TextAlign.center)),
    ]));
  }
}
