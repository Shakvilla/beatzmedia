import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../design_system/tokens.dart';
import '../../application/cart_provider.dart';

class CartScreen extends ConsumerWidget {
  const CartScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(cartProvider);
    final cart = state.cart;
    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar:
          AppBar(backgroundColor: AppColors.canvas, title: const Text('Cart')),
      body: state.isLoading && cart == null
          ? const Center(
              child: CircularProgressIndicator(color: AppColors.primary))
          : cart == null || cart.items.isEmpty
              ? Center(
                  child: Column(mainAxisSize: MainAxisSize.min, children: [
                  const Icon(Icons.shopping_cart_outlined,
                      size: 48, color: AppColors.textMuted),
                  const SizedBox(height: 12),
                  const Text('Your cart is empty',
                      style: AppTypography.bodySmall),
                  const SizedBox(height: 12),
                  FilledButton(
                      onPressed: () => context.go('/home'),
                      child: const Text('Browse catalog'))
                ]))
              : RefreshIndicator(
                  onRefresh: () => ref.read(cartProvider.notifier).load(),
                  child: ListView(padding: const EdgeInsets.all(16), children: [
                    if (state.duplicateError != null)
                      Container(
                          padding: const EdgeInsets.all(10),
                          decoration: BoxDecoration(
                              color: AppColors.error,
                              borderRadius: BorderRadius.circular(AppRadii.sm)),
                          child: Text(state.duplicateError!,
                              style: const TextStyle(
                                  color: Colors.white, fontSize: 12))),
                    if (state.error != null)
                      Padding(
                          padding: const EdgeInsets.only(top: 8),
                          child: Text('Error: ${state.error}',
                              style: const TextStyle(
                                  color: AppColors.error, fontSize: 12))),
                    ...cart.items.map((item) => ListTile(
                          leading: item.image != null
                              ? ClipRRect(
                                  borderRadius: BorderRadius.circular(6),
                                  child: Image.network(item.image!,
                                      width: 44,
                                      height: 44,
                                      fit: BoxFit.cover,
                                      errorBuilder: (_, __, ___) => Container(
                                          width: 44,
                                          height: 44,
                                          color: AppColors.surfaceElevated,
                                          child: const Icon(Icons.music_note,
                                              size: 18,
                                              color: AppColors.textMuted))))
                              : Container(
                                  width: 44,
                                  height: 44,
                                  decoration: BoxDecoration(
                                      color: AppColors.surfaceElevated,
                                      borderRadius: BorderRadius.circular(6)),
                                  child: const Icon(Icons.music_note,
                                      size: 18, color: AppColors.textMuted)),
                          title: Text(item.title ?? item.refId,
                              style:
                                  AppTypography.title.copyWith(fontSize: 13)),
                          subtitle: Text(
                              '${item.kind} • GHS ${item.price?.amount ?? 0}',
                              style: AppTypography.mono),
                          trailing:
                              Row(mainAxisSize: MainAxisSize.min, children: [
                            IconButton(
                                icon: const Icon(Icons.remove, size: 18),
                                onPressed: item.qty > 1
                                    ? () => ref
                                        .read(cartProvider.notifier)
                                        .updateQty(item.lineId, item.qty - 1)
                                    : null),
                            Text('${item.qty}', style: AppTypography.mono),
                            IconButton(
                                icon: const Icon(Icons.add, size: 18),
                                onPressed: () => ref
                                    .read(cartProvider.notifier)
                                    .updateQty(item.lineId, item.qty + 1)),
                            IconButton(
                                icon: const Icon(Icons.delete_outline,
                                    size: 18, color: AppColors.error),
                                onPressed: () => ref
                                    .read(cartProvider.notifier)
                                    .remove(item.lineId)),
                          ]),
                        )),
                    const Divider(color: AppColors.border),
                    _row('Subtotal', 'GHS ${cart.subtotal.amount}'),
                    _row('Fee', 'GHS ${cart.fee.amount}'),
                    const SizedBox(height: 8),
                    _row('Total', 'GHS ${cart.total.amount}', isTotal: true),
                    const SizedBox(height: 24),
                    FilledButton(
                        onPressed: () => context.push('/checkout'),
                        child: const Text('Checkout')),
                  ]),
                ),
    );
  }

  Widget _row(String label, String value, {bool isTotal = false}) =>
      Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
        Text(label,
            style: isTotal ? AppTypography.title : AppTypography.bodySmall),
        Text(value,
            style: isTotal
                ? AppTypography.title.copyWith(color: AppColors.primary)
                : AppTypography.bodySmall)
      ]);
}
