import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../design_system/tokens.dart';
import '../../application/onboarding_provider.dart';

class GenreOnboardingScreen extends ConsumerStatefulWidget {
  const GenreOnboardingScreen({super.key});
  @override
  ConsumerState<GenreOnboardingScreen> createState() =>
      _GenreOnboardingScreenState();
}

class _GenreOnboardingScreenState extends ConsumerState<GenreOnboardingScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(onboardingProvider.notifier).loadGenres();
    });
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(onboardingProvider);
    final notifier = ref.read(onboardingProvider.notifier);
    final selected = state.selectedGenres;
    final canSubmit =
        selected.length >= 3 && state.status != OnboardingStatus.loading;

    return Scaffold(
      backgroundColor: AppColors.canvas,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            const SizedBox(height: 12),
            const Text('What do you love?', style: AppTypography.displayMedium),
            const SizedBox(height: 8),
            Text(
                'Select at least 3 genres — you can change them later in settings.',
                style: AppTypography.bodySmall
                    .copyWith(color: AppColors.textSecondary)),
            const SizedBox(height: 8),
            Text('${selected.length} / 3 selected', style: AppTypography.mono),
            const SizedBox(height: 16),
            if (state.error != null)
              Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                      color: AppColors.error,
                      borderRadius: BorderRadius.circular(AppRadii.sm)),
                  child: Text(state.error!,
                      style:
                          const TextStyle(color: Colors.white, fontSize: 12))),
            if (state.error != null) const SizedBox(height: 12),
            Expanded(child: _buildGrid(state, notifier)),
            const SizedBox(height: 16),
            FilledButton(
              onPressed: canSubmit
                  ? () async {
                      await notifier.submitGenres();
                      if (context.mounted &&
                          ref.read(onboardingProvider).status ==
                              OnboardingStatus.success) context.go('/home');
                    }
                  : null,
              child: state.status == OnboardingStatus.loading
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: Colors.white))
                  : Text('Continue (${selected.length}/3)'),
            ),
            const SizedBox(height: 8),
            TextButton(
                onPressed: () => context.go('/welcome'),
                child: const Text('Skip for now')),
          ]),
        ),
      ),
    );
  }

  Widget _buildGrid(OnboardingState state, OnboardingNotifier notifier) {
    if (state.status == OnboardingStatus.loading && state.allGenres.isEmpty) {
      return const Center(
          child: CircularProgressIndicator(color: AppColors.primary));
    }
    if (state.allGenres.isEmpty) {
      return Center(
          child: Column(mainAxisSize: MainAxisSize.min, children: [
        const Icon(Icons.music_note_outlined,
            size: 48, color: AppColors.textMuted),
        const SizedBox(height: 12),
        const Text('No genres available', style: AppTypography.bodySmall),
        const SizedBox(height: 12),
        OutlinedButton(
            onPressed: () => notifier.loadGenres(), child: const Text('Retry')),
      ]));
    }
    return GridView.builder(
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
          crossAxisCount: 2,
          childAspectRatio: 2.8,
          crossAxisSpacing: 12,
          mainAxisSpacing: 12),
      itemCount: state.allGenres.length,
      itemBuilder: (context, i) {
        final genre = state.allGenres[i];
        final isSelected =
            state.selectedGenres.any((g) => g.name == genre.name);
        final isError = state.invalidGenres.contains(genre.name);
        return GestureDetector(
          onTap: () => notifier.toggleGenre(genre),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 180),
            decoration: BoxDecoration(
              color: isSelected ? AppColors.primary : AppColors.surfaceElevated,
              borderRadius: BorderRadius.circular(AppRadii.md),
              border: Border.all(
                  color: isError
                      ? AppColors.error
                      : (isSelected ? AppColors.primary : AppColors.border),
                  width: isError ? 2 : 1),
            ),
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
            child: Row(children: [
              Icon(isSelected ? Icons.check_circle : Icons.music_note,
                  size: 18,
                  color: isSelected ? Colors.white : AppColors.textMuted),
              const SizedBox(width: 8),
              Expanded(
                  child: Text(genre.name,
                      style: TextStyle(
                          fontFamily: AppTypography.displayFamily,
                          fontWeight: FontWeight.w600,
                          fontSize: 14,
                          color: isSelected
                              ? Colors.white
                              : AppColors.textPrimary))),
            ]),
          ),
        );
      },
    );
  }
}
