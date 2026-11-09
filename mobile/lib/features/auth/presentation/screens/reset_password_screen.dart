import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/errors/api_error.dart';
import '../../../../design_system/tokens.dart';
import '../../application/auth_providers.dart';

class ResetPasswordScreen extends ConsumerStatefulWidget {
  const ResetPasswordScreen({super.key, required this.token});

  /// Token comes from the deep link query params. Never logged or rendered.
  final String token;

  @override
  ConsumerState<ResetPasswordScreen> createState() =>
      _ResetPasswordScreenState();
}

class _ResetPasswordScreenState extends ConsumerState<ResetPasswordScreen> {
  final _passwordController = TextEditingController();
  final _confirmController = TextEditingController();
  final _formKey = GlobalKey<FormState>();
  bool _isLoading = false;
  String? _serverError;

  @override
  void dispose() {
    _passwordController.dispose();
    _confirmController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar: AppBar(
        backgroundColor: AppColors.canvas,
        elevation: 0,
        title: const Text('Set New Password'),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
          child: Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const SizedBox(height: 24),
                const Text(
                  'Set New Password',
                  style: AppTypography.displayMedium,
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 8),
                const Text(
                  'Enter a new password for your account',
                  style: AppTypography.bodySmall,
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 32),
                if (_serverError != null)
                  Container(
                    padding: const EdgeInsets.all(12),
                    color: AppColors.error,
                    child: Text(
                      _serverError!,
                      style: const TextStyle(color: Colors.white, fontSize: 12),
                      textAlign: TextAlign.center,
                    ),
                  ),
                const SizedBox(height: 24),
                TextFormField(
                  controller: _passwordController,
                  decoration: InputDecoration(
                    labelText: 'New password',
                    border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(AppRadii.md)),
                    filled: true,
                    fillColor: AppColors.surfaceElevated,
                    suffixIcon: IconButton(
                      icon: const Icon(
                        Icons.visibility_off,
                        color: AppColors.textMuted,
                      ),
                      onPressed: () {},
                    ),
                  ),
                  obscureText: true,
                  validator: (value) {
                    if (value == null || value.isEmpty) {
                      return 'Enter a password';
                    }
                    if (value.length < 6) {
                      return 'Password must be at least 6 characters';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _confirmController,
                  decoration: InputDecoration(
                    labelText: 'Confirm password',
                    border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(AppRadii.md)),
                    filled: true,
                    fillColor: AppColors.surfaceElevated,
                    suffixIcon: IconButton(
                      icon: const Icon(
                        Icons.visibility_off,
                        color: AppColors.textMuted,
                      ),
                      onPressed: () {},
                    ),
                  ),
                  obscureText: true,
                  validator: (value) {
                    if (value == null || value.isEmpty) {
                      return 'Confirm your password';
                    }
                    if (value != _passwordController.text) {
                      return 'Passwords do not match';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 24),
                ElevatedButton(
                  onPressed: _isLoading
                      ? null
                      : () async {
                          if (_formKey.currentState!.validate()) {
                            setState(() {
                              _isLoading = true;
                              _serverError = null;
                            });
                            try {
                              await ref
                                  .read(authProvider.notifier)
                                  .resetPassword(widget.token,
                                      _passwordController.text.trim());
                              if (!context.mounted) return;
                              final auth = ref.read(authProvider);
                              if (auth.status == AuthStatus.resetCompleted) {
                                context.go('/login');
                              } else {
                                setState(() {
                                  _isLoading = false;
                                  _serverError =
                                      auth.error ?? 'Unable to reset password.';
                                });
                              }
                            } on ApiError catch (e) {
                              if (!mounted) return;
                              setState(() {
                                _isLoading = false;
                                _serverError = _mapApiError(e);
                              });
                            } catch (_) {
                              if (!mounted) return;
                              setState(() {
                                _isLoading = false;
                                _serverError =
                                    'Failed to connect. Please check your network.';
                              });
                            }
                          }
                        },
                  style: FilledButton.styleFrom(
                    minimumSize: const Size.fromHeight(48),
                  ),
                  child: _isLoading
                      ? const SizedBox(
                          width: 24,
                          height: 24,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        )
                      : const Text('Set password'),
                ),
                const SizedBox(height: 32),
                const Text(
                  'Remember your password?',
                  style: AppTypography.bodySmall,
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 8),
                OutlinedButton(
                  onPressed: () => context.go('/login'),
                  child: const Text('Sign in'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  String _mapApiError(ApiError e) {
    if (e.field != null && e.message.isNotEmpty) {
      return 'Error: ${e.message}';
    }
    return 'An error occurred. Please try again.';
  }
}
