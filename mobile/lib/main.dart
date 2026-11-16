import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'app/router.dart';
import 'design_system/tokens.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const ProviderScope(child: BeatzclikApp()));
}

class BeatzclikApp extends ConsumerWidget {
  const BeatzclikApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(appRouterProvider);
    return MaterialApp.router(
      title: 'BeatzClik',
      theme: buildAppTheme(),
      routerConfig: router,
      debugShowCheckedModeBanner: false,
    );
  }
}
