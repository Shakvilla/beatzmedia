import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../design_system/tokens.dart';
import '../../data/commerce_repository.dart';
import '../../data/dto/commerce_dto.dart';

class ReceiptScreen extends ConsumerStatefulWidget {
  const ReceiptScreen({super.key, required this.orderId});
  final String orderId;
  @override
  ConsumerState<ReceiptScreen> createState() => _ReceiptScreenState();
}

class _ReceiptScreenState extends ConsumerState<ReceiptScreen> {
  OrderSnapshotDto? _order;
  bool _loading = true;
  Object? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final o = await CommerceRepository().getOrder(widget.orderId);
      if (mounted)
        setState(() {
          _order = o;
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
          appBar: AppBar(
              backgroundColor: AppColors.canvas, title: const Text('Receipt')),
          body: Center(
              child: Text('Failed: $_error',
                  style: const TextStyle(color: AppColors.error))));
    final order = _order!;
    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar: AppBar(
          backgroundColor: AppColors.canvas, title: const Text('Receipt')),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
                color: AppColors.surface,
                borderRadius: BorderRadius.circular(AppRadii.md),
                border: Border.all(color: AppColors.gold)),
            child: Column(children: [
              const Icon(Icons.check_circle, size: 48, color: AppColors.gold),
              const SizedBox(height: 8),
              const Text('Payment confirmed',
                  style: AppTypography.displayMedium),
              Text('Order ${order.reference}', style: AppTypography.mono),
              const SizedBox(height: 4),
              Text('Status: ${order.status}', style: AppTypography.monoSmall)
            ])),
        const SizedBox(height: 24),
        const Text('Items', style: AppTypography.displayMedium),
        const SizedBox(height: 8),
        ...order.lineItems.map((l) => ListTile(
            leading: l.image != null
                ? ClipRRect(
                    borderRadius: BorderRadius.circular(6),
                    child: Image.network(l.image!,
                        width: 44, height: 44, fit: BoxFit.cover))
                : Container(
                    width: 44,
                    height: 44,
                    color: AppColors.surfaceElevated,
                    child: const Icon(Icons.receipt,
                        size: 18, color: AppColors.textMuted)),
            title: Text(l.title,
                style: AppTypography.title.copyWith(fontSize: 13)),
            subtitle: l.subtitle != null
                ? Text(l.subtitle!, style: AppTypography.mono)
                : null,
            trailing: Text('GHS ${l.price.amount}',
                style: AppTypography.mono.copyWith(color: AppColors.primary)))),
        const Divider(color: AppColors.border),
        _row('Subtotal', 'GHS ${order.subtotal.amount}'),
        _row('Fee', 'GHS ${order.fee.amount}'),
        const SizedBox(height: 8),
        _row('Total', 'GHS ${order.total.amount}', isTotal: true),
        const SizedBox(height: 24),
        FilledButton(onPressed: () {}, child: const Text('Continue')),
      ]),
    );
  }

  Widget _row(String label, String value, {bool isTotal = false}) =>
      Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
        Text(label,
            style: isTotal ? AppTypography.title : AppTypography.bodySmall),
        Text(value,
            style: isTotal
                ? AppTypography.title.copyWith(color: AppColors.gold)
                : AppTypography.bodySmall)
      ]);
}
