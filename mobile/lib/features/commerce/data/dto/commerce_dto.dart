class MoneyDto { const MoneyDto({required this.amount, required this.currency}); final int amount; final String currency; factory MoneyDto.fromJson(Map<String, dynamic> j) => MoneyDto(amount: (j['amount'] as num).toInt(), currency: j['currency'] as String); }

class CartItemDto {
  const CartItemDto({required this.lineId, required this.id, required this.kind, required this.refId, this.qty = 1, this.metadata, this.title, this.artist, this.price, this.image});
  final String lineId; final String id; final String kind; final String refId; final int qty; final Map<String, dynamic>? metadata; final String? title; final String? artist; final MoneyDto? price; final String? image;
  factory CartItemDto.fromJson(Map<String, dynamic> j) => CartItemDto(lineId: j['lineId'] as String? ?? j['id'] as String, id: j['id'] as String, kind: j['kind'] as String, refId: j['refId'] as String, qty: (j['qty'] as num?)?.toInt() ?? 1, metadata: j['metadata'] as Map<String, dynamic>?, title: j['title'] as String?, artist: j['artist'] as String?, price: j['price'] != null ? MoneyDto.fromJson(j['price'] as Map<String, dynamic>) : null, image: j['image'] as String?);
}

class CartDto {
  const CartDto({required this.items, required this.subtotal, required this.fee, required this.total, required this.count});
  final List<CartItemDto> items; final MoneyDto subtotal; final MoneyDto fee; final MoneyDto total; final int count;
  factory CartDto.fromJson(Map<String, dynamic> j) {
    MoneyDto parse(dynamic v) => v is Map ? MoneyDto.fromJson(v as Map<String, dynamic>) : MoneyDto(amount: (v as num).toInt(), currency: 'GHS');
    return CartDto(items: (j['items'] as List).map((e) => CartItemDto.fromJson(e as Map<String, dynamic>)).toList(), subtotal: parse(j['subtotal']), fee: parse(j['fee']), total: parse(j['total']), count: (j['count'] as num?)?.toInt() ?? (j['items'] as List).length);
  }
}

class OrderLineDto { const OrderLineDto({required this.title, this.subtitle, this.image, required this.price}); final String title; final String? subtitle; final String? image; final MoneyDto price; factory OrderLineDto.fromJson(Map<String, dynamic> j) => OrderLineDto(title: j['title'] as String, subtitle: j['subtitle'] as String?, image: j['image'] as String?, price: MoneyDto.fromJson(j['price'] as Map<String, dynamic>)); }

class OrderSnapshotDto {
  const OrderSnapshotDto({required this.orderId, required this.reference, required this.status, this.paymentIntentId, this.checkoutUrl, required this.subtotal, required this.fee, required this.total, this.lineItems = const [], this.createdAt});
  final String orderId; final String reference; final String status; final String? paymentIntentId; final String? checkoutUrl; final MoneyDto subtotal; final MoneyDto fee; final MoneyDto total; final List<OrderLineDto> lineItems; final String? createdAt;
  factory OrderSnapshotDto.fromJson(Map<String, dynamic> j) => OrderSnapshotDto(orderId: j['orderId'] as String? ?? j['id'] as String, reference: j['reference'] as String? ?? '', status: j['status'] as String, paymentIntentId: j['paymentIntentId'] as String?, checkoutUrl: j['checkoutUrl'] as String?, subtotal: MoneyDto.fromJson(j['subtotal'] as Map<String, dynamic>), fee: MoneyDto.fromJson(j['fee'] as Map<String, dynamic>), total: MoneyDto.fromJson(j['total'] as Map<String, dynamic>), lineItems: (j['lineItems'] as List?)?.map((e) => OrderLineDto.fromJson(e as Map<String, dynamic>)).toList() ?? [], createdAt: j['createdAt'] as String?);
}
