import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../design_system/tokens.dart';
import '../../application/auth_providers.dart';

class ForgotPasswordScreen extends ConsumerStatefulWidget {
  const ForgotPasswordScreen({super.key});
  @override
  ConsumerState<ForgotPasswordScreen> createState() => _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState extends ConsumerState<ForgotPasswordScreen> {
  final _formKey = GlobalKey<FormState>();
  final _emailController = TextEditingController();
  bool _sent = false;

  @override
  void dispose() {
    _emailController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authProvider);
    final isLoading = authState.status == AuthStatus.loading;
    final error = authState.error;

    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar: AppBar(backgroundColor: AppColors.canvas, leading: IconButton(icon: const Icon(Icons.arrow_back), onPressed: () => context.go('/login'))),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
          child: _sent ? _buildSent() : Form(
            key: _formKey,
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              const Text('Forgot password?', style: AppTypography.displayMedium),
              const SizedBox(height: 8),
              const Text('Enter your email and we will send you a reset link.', style: AppTypography.bodySmall),
              const SizedBox(height: 32),
              if (error != null)
                Container(padding: const EdgeInsets.all(12), decoration: BoxDecoration(color: AppColors.error, borderRadius: BorderRadius.circular(AppRadii.sm)), child: Text(error, style: const TextStyle(color: Colors.white, fontSize: 12))),
              if (error != null) const SizedBox(height: 16),
              TextFormField(
                controller: _emailController,
                decoration: InputDecoration(labelText: 'Email', hintText: 'you@email.com', border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppRadii.md)), filled: true, fillColor: AppColors.surfaceElevated),
                keyboardType: TextInputType.emailAddress,
                validator: (v) => (v == null || v.isEmpty) ? 'Enter your email' : (!v.contains('@') ? 'Enter a valid email' : null),
              ),
              const SizedBox(height: 24),
              FilledButton(
                onPressed: isLoading ? null : () async {
                  if (_formKey.currentState!.validate()) {
                    await ref.read(authProvider.notifier).requestPasswordReset(_emailController.text.trim());
                    if (mounted && ref.read(authProvider).error == null) setState(() => _sent = true);
                  }
                },
                child: isLoading ? const SizedBox(width: 24, height: 24, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white)) : const Text('Send reset link'),
              ),
              const SizedBox(height: 12),
              TextButton(onPressed: () => context.go('/login'), child: const Text('Back to sign in')),
            ]),
          ),
        ),
      ),
    );
  }

  Widget _buildSent() {
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      const Icon(Icons.mark_email_read_outlined, size: 64, color: AppColors.primary),
      const SizedBox(height: 24),
      const Text('Check your email', style: AppTypography.displayMedium, textAlign: TextAlign.center),
      const SizedBox(height: 12),
      const Text('If an account exists for that email, you will receive a password reset link. The link expires soon and can be used only once. Do not share it.', style: AppTypography.bodySmall, textAlign: TextAlign.center),
      const SizedBox(height: 24),
      FilledButton(onPressed: () => context.go('/login'), child: const Text('Back to sign in')),
      const SizedBox(height: 12),
      const Text('No OTP was issued — use the email link to reset your password.', style: AppTypography.monoSmall, textAlign: TextAlign.center),
    ]);
  }
}
