import 'package:beatzclik_fan/main.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('BeatzclikApp builds', (tester) async {
    await tester.pumpWidget(const ProviderScope(child: BeatzclikApp()));
    await tester.pump();
    expect(find.text('BeatzClik'), findsOneWidget);
  });
}
