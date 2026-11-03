import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../design_system/tokens.dart';
import '../../application/checkout_provider.dart';
import '../../data/commerce_repository.dart';

class CheckoutScreen extends ConsumerStatefulWidget {
  const CheckoutScreen({super.key});
  @override
  ConsumerState<CheckoutScreen> createState() => _CheckoutScreenState();
}

class _CheckoutScreenState extends ConsumerState<CheckoutScreen> with WidgetsBindingObserver {
  List<String> _providers = [];
  String? _selected;
  bool _loadingProviders = true;

  @override
  void initState() { super.initState(); WidgetsBinding.instance.addObserver(this); _loadProviders(); }

  Future<void> _loadProviders() async {
    try { final list = await CommerceRepository().getProviders(); if (mounted) setState(() { _providers = list; _selected = list.isNotEmpty ? list.first : null; _loadingProviders = false; }); } catch (_) { if (mounted) setState(() => _loadingProviders = false); }
  }

  @override
  void dispose() { WidgetsBinding.instance.removeObserver(this); super.dispose(); }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && ref.read(checkoutProvider).status == CheckoutStatus.polling) {
      // polling continues automatically via Timer, no extra action needed
    }
  }

  @override
  Widget build(BuildContext context) {
    final checkout = ref.watch(checkoutProvider);
    ref.listen(checkoutProvider, (prev, next) {
      if (next.status == CheckoutStatus.succeeded && next.order != null) context.go('/receipt/${next.order!.orderId}');
    });

    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar: AppBar(backgroundColor: AppColors.canvas, title: const Text('Checkout')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          if (_loadingProviders) const LinearProgressIndicator(color: AppColors.primary) else if (_providers.isEmpty) const Text('No payment providers available', style: TextStyle(color: AppColors.error)) else ...[
            const Text('Select payment method', style: AppTypography.title),
            const SizedBox(height: 12),
            ..._providers.map((p) => RadioListTile<String>(title: Text(p.toUpperCase(), style: AppTypography.body), subtitle: Text(p == 'mtn' || p.contains('momo') ? 'MTN MoMo — Ghana' : p, style: AppTypography.mono), value: p, groupValue: _selected, activeColor: AppColors.primary, onChanged: (v) => setState(() => _selected = v))),
          ],
          const SizedBox(height: 16),
          if (checkout.order?.checkoutUrl != null) Container(padding: const EdgeInsets.all(12), decoration: BoxDecoration(color: AppColors.surfaceElevated, borderRadius: BorderRadius.circular(AppRadii.md), border: Border.all(color: AppColors.gold)), child: Column(children: [const Text('Card payment requires hosted checkout', style: AppTypography.bodySmall), const SizedBox(height: 8), Text(checkout.order!.checkoutUrl!, style: AppTypography.mono.copyWith(color: AppColors.primary)), const SizedBox(height: 8), const Text('You will be redirected — do not close the app. Settlement is confirmed server-side.', style: AppTypography.monoSmall)])),
          const Spacer(),
          _statusBanner(checkout),
          const SizedBox(height: 12),
          FilledButton(
            onPressed: _selected == null || checkout.status == CheckoutStatus.processing || checkout.status == CheckoutStatus.polling ? null : () => ref.read(checkoutProvider.notifier).startCheckout(_selected!),
            child: checkout.status == CheckoutStatus.processing || checkout.status == CheckoutStatus.polling ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white)) : const Text('Pay now'),
          ),
          if (checkout.status == CheckoutStatus.failed || checkout.status == CheckoutStatus.timedOut || checkout.status == CheckoutStatus.cancelled) ...[
            const SizedBox(height: 8),
            OutlinedButton(onPressed: () => ref.read(checkoutProvider.notifier).retryWithSameKey(), child: const Text('Retry')),
          ],
        ]),
      ),
    );
  }

  Widget _statusBanner(CheckoutState s) {
    switch (s.status) {
      case CheckoutStatus.processing: return Container(padding: const EdgeInsets.all(10), decoration: BoxDecoration(color: AppColors.surfaceElevated, borderRadius: BorderRadius.circular(AppRadii.sm)), child: const Text('Initiating payment…', style: AppTypography.mono));
      case CheckoutStatus.polling: return Container(padding: const EdgeInsets.all(10), decoration: BoxDecoration(color: AppColors.surfaceElevated, borderRadius: BorderRadius.circular(AppRadii.sm)), child: Row(children: [const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: AppColors.primary)), const SizedBox(width: 10), const Text('Waiting for settlement…', style: AppTypography.mono)]));
      case CheckoutStatus.failed: return Container(padding: const EdgeInsets.all(10), decoration: BoxDecoration(color: AppColors.error, borderRadius: BorderRadius.circular(AppRadii.sm)), child: Text('Payment failed: ${s.error}', style: const TextStyle(color: Colors.white, fontSize: 12)));
      case CheckoutStatus.cancelled: return Container(padding: const EdgeInsets.all(10), decoration: BoxDecoration(color: AppColors.error, borderRadius: BorderRadius.circular(AppRadii.sm)), child: const Text('Payment cancelled', style: TextStyle(color: Colors.white, fontSize: 12)));
      case CheckoutStatus.timedOut: return Container(padding: const EdgeInsets.all(10), decoration: BoxDecoration(color: AppColors.error, borderRadius: BorderRadius.circular(AppRadii.sm)), child: const Text('Timed out — check orders or retry', style: TextStyle(color: Colors.white, fontSize: 12)));
      case CheckoutStatus.succeeded: return Container(padding: const EdgeInsets.all(10), decoration: BoxDecoration(color: AppColors.success, borderRadius: BorderRadius.circular(AppRadii.sm)), child: const Text('Payment succeeded', style: TextStyle(color: Colors.white, fontSize: 12)));
      case CheckoutStatus.idle: return const SizedBox.shrink();
    }
  }
}
