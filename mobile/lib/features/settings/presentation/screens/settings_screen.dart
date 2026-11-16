import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../design_system/tokens.dart';
import '../../../../core/session/session_provider.dart';

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final account = ref.watch(sessionProvider).account;
    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar: AppBar(
          backgroundColor: AppColors.canvas,
          title: const Text('Profile & Settings')),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        if (account != null)
          ListTile(
              leading: CircleAvatar(
                  backgroundColor: AppColors.surfaceElevated,
                  child: Text(account.name[0].toUpperCase(),
                      style: AppTypography.title)),
              title: Text(account.name, style: AppTypography.title),
              subtitle: Text(account.email, style: AppTypography.mono)),
        const Divider(color: AppColors.border),
        ListTile(
            leading: const Icon(Icons.person_outline,
                color: AppColors.textSecondary),
            title: const Text('Account', style: AppTypography.title),
            subtitle: const Text('Manage profile', style: AppTypography.mono),
            onTap: () {}),
        ListTile(
            leading: const Icon(Icons.palette_outlined,
                color: AppColors.textSecondary),
            title: const Text('Preferences', style: AppTypography.title),
            subtitle: const Text('Genres & taste', style: AppTypography.mono),
            onTap: () {}),
        SwitchListTile(
            title:
                const Text('Wi-Fi only downloads', style: AppTypography.title),
            subtitle:
                const Text('Only download on Wi-Fi', style: AppTypography.mono),
            value: true,
            activeColor: AppColors.primary,
            onChanged: (_) {}),
        SwitchListTile(
            title: const Text('Theme', style: AppTypography.title),
            subtitle:
                const Text('Dark • Ghana night', style: AppTypography.mono),
            value: true,
            activeColor: AppColors.primary,
            onChanged: (_) {}),
        ListTile(
            leading: const Icon(Icons.description_outlined,
                color: AppColors.textSecondary),
            title: const Text('Legal & About', style: AppTypography.title),
            subtitle: const Text('Terms, Privacy', style: AppTypography.mono),
            onTap: () {}),
        ListTile(
            leading: const Icon(Icons.cleaning_services_outlined,
                color: AppColors.textSecondary),
            title: const Text('Clear cache', style: AppTypography.title),
            subtitle: const Text('Does not remove ownership',
                style: AppTypography.mono),
            onTap: () => ScaffoldMessenger.of(context)
                .showSnackBar(const SnackBar(content: Text('Cache cleared')))),
        const Divider(color: AppColors.border),
        FilledButton(
            onPressed: () async {
              await ref.read(sessionProvider.notifier).logout();
              if (context.mounted) Navigator.of(context).pop();
            },
            style: FilledButton.styleFrom(backgroundColor: AppColors.error),
            child: const Text('Sign out')),
        const SizedBox(height: 8),
        const Text(
            'Sign out stops playback, clears protected memory, and makes local downloads unavailable to next session (account-scoped)',
            style: AppTypography.monoSmall,
            textAlign: TextAlign.center),
      ]),
    );
  }
}
