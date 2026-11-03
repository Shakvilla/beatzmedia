import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../data/commerce_repository.dart';
import '../data/dto/commerce_dto.dart';

class CartState {
  const CartState({this.cart, this.isLoading = false, this.error, this.duplicateError});
  final CartDto? cart; final bool isLoading; final Object? error; final String? duplicateError;
  CartState copyWith({CartDto? cart, bool? isLoading, Object? error, String? duplicateError}) => CartState(cart: cart ?? this.cart, isLoading: isLoading ?? this.isLoading, error: error, duplicateError: duplicateError);
}

class CartNotifier extends StateNotifier<CartState> {
  CartNotifier(this._repo) : super(const CartState());
  final CommerceRepository _repo;
  Future<void> load() async { state = state.copyWith(isLoading: true, error: null); try { final c = await _repo.getCart(); state = CartState(cart: c, isLoading: false); } catch (e) { state = state.copyWith(isLoading: false, error: e); } }
  Future<void> add({required String id, required String kind, required String refId, int qty = 1, Map<String, dynamic>? metadata}) async { state = state.copyWith(isLoading: true, duplicateError: null); try { final c = await _repo.addItem(id: id, kind: kind, refId: refId, qty: qty, metadata: metadata); state = CartState(cart: c, isLoading: false); } catch (e) { final msg = e.toString(); if (msg.contains('ALREADY_OWNED') || msg.contains('DUPLICATE') || msg.contains('409')) { state = state.copyWith(isLoading: false, duplicateError: msg); } else { state = state.copyWith(isLoading: false, error: e); } } }
  Future<void> updateQty(String lineId, int qty) async { state = state.copyWith(isLoading: true); try { final c = await _repo.updateQty(lineId, qty); state = CartState(cart: c, isLoading: false); } catch (e) { state = state.copyWith(isLoading: false, error: e); } }
  Future<void> remove(String lineId) async { state = state.copyWith(isLoading: true); try { final c = await _repo.removeItem(lineId); state = CartState(cart: c, isLoading: false); } catch (e) { state = state.copyWith(isLoading: false, error: e); } }
}

final commerceRepositoryProvider = Provider<CommerceRepository>((ref) => CommerceRepository());
final cartProvider = StateNotifierProvider<CartNotifier, CartState>((ref) => CartNotifier(ref.watch(commerceRepositoryProvider))..load());
