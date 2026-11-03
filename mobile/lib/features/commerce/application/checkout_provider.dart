import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/api/api_client.dart';
import '../data/commerce_repository.dart';
import '../data/dto/commerce_dto.dart';
import 'cart_provider.dart' show commerceRepositoryProvider;

enum CheckoutStatus { idle, processing, polling, succeeded, failed, cancelled, timedOut }

class CheckoutState {
  const CheckoutState({this.order, this.status = CheckoutStatus.idle, this.error, this.idempotencyKey});
  final OrderSnapshotDto? order; final CheckoutStatus status; final Object? error; final String? idempotencyKey;
  CheckoutState copyWith({OrderSnapshotDto? order, CheckoutStatus? status, Object? error, String? idempotencyKey}) => CheckoutState(order: order ?? this.order, status: status ?? this.status, error: error, idempotencyKey: idempotencyKey ?? this.idempotencyKey);
}

class CheckoutNotifier extends StateNotifier<CheckoutState> {
  CheckoutNotifier(this._repo) : super(const CheckoutState());
  final CommerceRepository _repo;
  Timer? _pollTimer;
  int _attempt = 0;

  Future<void> startCheckout(String paymentMethodId) async {
    final key = ApiClient.newIdempotencyKey();
    state = CheckoutState(status: CheckoutStatus.processing, idempotencyKey: key);
    try {
      final order = await _repo.checkout(paymentMethodId, key);
      if (order.checkoutUrl != null && order.checkoutUrl!.isNotEmpty) {
        state = CheckoutState(order: order, status: CheckoutStatus.processing, idempotencyKey: key);
        return;
      }
      state = CheckoutState(order: order, status: CheckoutStatus.polling, idempotencyKey: key);
      _poll(order.orderId);
    } catch (e) {
      final msg = e.toString();
      if (msg.contains('PROVIDER_DISABLED')) { state = CheckoutState(status: CheckoutStatus.failed, error: 'Payment provider disabled', idempotencyKey: key); }
      else { state = CheckoutState(status: CheckoutStatus.failed, error: e, idempotencyKey: key); }
    }
  }

  Future<void> retryWithSameKey() async {
    final key = state.idempotencyKey; if (key == null) return;
    final orderId = state.order?.orderId; if (orderId == null) return;
    state = state.copyWith(status: CheckoutStatus.polling);
    _poll(orderId);
  }

  void _poll(String orderId) {
    _attempt = 0;
    _pollTimer?.cancel();
    _pollTimer = Timer.periodic(const Duration(seconds: 2), (t) async {
      _attempt++;
      try {
        final order = await _repo.getOrder(orderId);
        final s = order.status.toLowerCase();
        if (s == 'succeeded' || s == 'paid' || s == 'confirmed') {
          t.cancel(); state = state.copyWith(order: order, status: CheckoutStatus.succeeded);
        } else if (s == 'failed') {
          t.cancel(); state = state.copyWith(order: order, status: CheckoutStatus.failed);
        } else if (s == 'cancelled') {
          t.cancel(); state = state.copyWith(order: order, status: CheckoutStatus.cancelled);
        } else if (_attempt >= 15) {
          t.cancel(); state = state.copyWith(order: order, status: CheckoutStatus.timedOut);
        } else {
          state = state.copyWith(order: order, status: CheckoutStatus.polling);
        }
      } catch (e) {
        if (_attempt >= 5) { t.cancel(); state = state.copyWith(status: CheckoutStatus.failed, error: e); }
      }
    });
  }

  @override
  void dispose() { _pollTimer?.cancel(); super.dispose(); }
}

final checkoutProvider = StateNotifierProvider<CheckoutNotifier, CheckoutState>((ref) => CheckoutNotifier(ref.watch(commerceRepositoryProvider)));
