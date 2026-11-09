import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../design_system/tokens.dart';
import '../../application/auth_providers.dart';

class SignupScreen extends ConsumerStatefulWidget {
  const SignupScreen({super.key});
  @override
  ConsumerState<SignupScreen> createState() => _SignupScreenState();
}

class _SignupScreenState extends ConsumerState<SignupScreen> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _obscurePassword = true;

  @override
  void dispose() {
    _nameController.dispose();
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authProvider);
    final isLoading = authState.status == AuthStatus.loading;
    final error = authState.error;

    ref.listen(authProvider, (prev, next) {
      if (next.status == AuthStatus.authenticated) context.go('/onboarding');
    });

    return Scaffold(
      backgroundColor: AppColors.canvas,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
          child: Form(
            key: _formKey,
            child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const SizedBox(height: 48),
                  const Text('Create Account',
                      style: AppTypography.displayMedium,
                      textAlign: TextAlign.center),
                  const SizedBox(height: 8),
                  const Text('Join BeatzClik',
                      style: AppTypography.bodySmall,
                      textAlign: TextAlign.center),
                  const SizedBox(height: 32),
                  if (error != null)
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                          color: AppColors.error,
                          borderRadius: BorderRadius.circular(AppRadii.sm)),
                      child: Text(error,
                          style: const TextStyle(
                              color: Colors.white, fontSize: 12),
                          textAlign: TextAlign.center),
                    ),
                  const SizedBox(height: 24),
                  TextFormField(
                    controller: _nameController,
                    decoration: InputDecoration(
                      labelText: 'Name',
                      border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(AppRadii.md)),
                      filled: true,
                      fillColor: AppColors.surfaceElevated,
                    ),
                    validator: (v) => (v == null || v.isEmpty)
                        ? 'Enter your name'
                        : (v.length < 2
                            ? 'Name must be at least 2 characters'
                            : null),
                  ),
                  const SizedBox(height: 16),
                  TextFormField(
                    controller: _emailController,
                    decoration: InputDecoration(
                      labelText: 'Email',
                      hintText: 'you@email.com',
                      border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(AppRadii.md)),
                      filled: true,
                      fillColor: AppColors.surfaceElevated,
                    ),
                    keyboardType: TextInputType.emailAddress,
                    validator: (v) => (v == null || v.isEmpty)
                        ? 'Enter your email'
                        : (!v.contains('@') ? 'Enter a valid email' : null),
                  ),
                  const SizedBox(height: 16),
                  TextFormField(
                    controller: _passwordController,
                    decoration: InputDecoration(
                      labelText: 'Password',
                      border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(AppRadii.md)),
                      filled: true,
                      fillColor: AppColors.surfaceElevated,
                      suffixIcon: IconButton(
                        icon: Icon(
                            _obscurePassword
                                ? Icons.visibility_off
                                : Icons.visibility,
                            color: AppColors.textMuted),
                        onPressed: () => setState(
                            () => _obscurePassword = !_obscurePassword),
                      ),
                    ),
                    obscureText: _obscurePassword,
                    validator: (v) => (v == null || v.isEmpty)
                        ? 'Enter your password'
                        : (v.length < 6
                            ? 'Password must be at least 6 characters'
                            : null),
                  ),
                  const SizedBox(height: 24),
                  FilledButton(
                    onPressed: isLoading
                        ? null
                        : () {
                            if (_formKey.currentState!.validate()) {
                              ref.read(authProvider.notifier).signup(
                                    _nameController.text.trim(),
                                    _emailController.text.trim(),
                                    _passwordController.text,
                                  );
                            }
                          },
                    child: isLoading
                        ? const SizedBox(
                            width: 24,
                            height: 24,
                            child: CircularProgressIndicator(
                                strokeWidth: 2, color: Colors.white))
                        : const Text('Create account'),
                  ),
                  const SizedBox(height: 12),
                  OutlinedButton(
                    onPressed: () => context.go('/login'),
                    style: OutlinedButton.styleFrom(
                        foregroundColor: AppColors.textPrimary,
                        side: const BorderSide(color: AppColors.border),
                        minimumSize: const Size.fromHeight(48),
                        shape: RoundedRectangleBorder(
                            borderRadius:
                                BorderRadius.circular(AppRadii.pill))),
                    child: const Text('Sign in'),
                  ),
                  const SizedBox(height: 24),
                  TextButton(
                      onPressed: () => context.go('/login'),
                      child: const Text('Already have an account? Sign in')),
                ]),
          ),
        ),
      ),
    );
  }
}
