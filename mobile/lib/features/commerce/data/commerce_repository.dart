import '../../../core/api/api_client.dart';
import 'dto/commerce_dto.dart';

class CommerceRepository {
  CommerceRepository({ApiClient? apiClient}) : _api = apiClient ?? ApiClient();
  final ApiClient _api;

  Future<CartDto> getCart() async {
    final json = await _api.get<Map<String, dynamic>>('/me/cart');
    return CartDto.fromJson(json);
  }

  Future<CartDto> addItem(
      {required String id,
      required String kind,
      required String refId,
      int qty = 1,
      Map<String, dynamic>? metadata}) async {
    final json = await _api.post<Map<String, dynamic>>('/me/cart/items', data: {
      'id': id,
      'kind': kind,
      'refId': refId,
      'qty': qty,
      if (metadata != null) 'metadata': metadata
    });
    return CartDto.fromJson(json);
  }

  Future<CartDto> updateQty(String lineId, int qty) async {
    final json = await _api.dio.patch<Map<String, dynamic>>(
        '/me/cart/items/$lineId',
        data: {'qty': qty}).then((r) => r.data!);
    return CartDto.fromJson(json);
  }

  Future<CartDto> removeItem(String lineId) async {
    final json = await _api.dio
        .delete<Map<String, dynamic>>('/me/cart/items/$lineId')
        .then((r) => r.data!);
    return CartDto.fromJson(json);
  }

  Future<List<String>> getProviders() async {
    final json = await _api.get<Map<String, dynamic>>('/payments/providers');
    return (json['enabled'] as List).cast<String>();
  }

  Future<OrderSnapshotDto> checkout(
      String paymentMethodId, String idempotencyKey) async {
    final json = await _api.post<Map<String, dynamic>>('/checkout',
        data: {'paymentMethodId': paymentMethodId},
        idempotencyKey: idempotencyKey);
    return OrderSnapshotDto.fromJson(json);
  }

  Future<OrderSnapshotDto> getOrder(String orderId) async {
    final json = await _api.get<Map<String, dynamic>>('/me/orders/$orderId');
    return OrderSnapshotDto.fromJson(json);
  }

  Future<List<OrderSnapshotDto>> getOrders() async {
    final list = await _api.get<List<dynamic>>('/me/orders');
    return list
        .map((e) => OrderSnapshotDto.fromJson(e as Map<String, dynamic>))
        .toList();
  }
}
