import 'package:flutter/material.dart';

/// Visual source: Beatzclik Fan App Prototype (Standalone) (1).html
/// Near-black canvas, warm off-white typography, magenta primary, gold ownership.
abstract class AppColors {
  static const canvas = Color(0xFF0A0A0B);
  static const surface = Color(0xFF1C1C1F);
  static const surfaceElevated = Color(0xFF242428);
  static const textPrimary = Color(0xFFF6F4EF);
  static const textSecondary = Color(0xFFA8A6A3);
  static const textMuted = Color(0xFF6E6E72);
  static const primary = Color(0xFFEE4094);
  static const primaryDark = Color(0xFFC7367D);
  static const gold = Color(0xFFD4AF37);
  static const goldSoft = Color(0xFFFFD86B);
  static const success = Color(0xFF22C55E);
  static const error = Color(0xFFEF4444);
  static const border = Color(0xFF2E2E32);
  static const glass = Color(0x1AF6F4EF);
}

abstract class AppTypography {
  // Bricolage Grotesque — display; JetBrains Mono — metadata
  static const displayFamily = 'BricolageGrotesque';
  static const monoFamily = 'JetBrainsMono';

  static const displayLarge = TextStyle(
    fontFamily: displayFamily,
    fontSize: 32, fontWeight: FontWeight.w800, color: AppColors.textPrimary, letterSpacing: -0.5,
  );
  static const displayMedium = TextStyle(
    fontFamily: displayFamily,
    fontSize: 24, fontWeight: FontWeight.w700, color: AppColors.textPrimary,
  );
  static const title = TextStyle(
    fontFamily: displayFamily,
    fontSize: 18, fontWeight: FontWeight.w600, color: AppColors.textPrimary,
  );
  static const body = TextStyle(
    fontSize: 15, fontWeight: FontWeight.w400, color: AppColors.textPrimary, height: 1.4,
  );
  static const bodySmall = TextStyle(
    fontSize: 13, fontWeight: FontWeight.w400, color: AppColors.textSecondary,
  );
  static const mono = TextStyle(
    fontFamily: monoFamily,
    fontSize: 11, fontWeight: FontWeight.w500, color: AppColors.textMuted, letterSpacing: 0.4,
  );
  static const monoSmall = TextStyle(
    fontFamily: monoFamily,
    fontSize: 10, fontWeight: FontWeight.w500, color: AppColors.textMuted, letterSpacing: 0.6,
  );
}

abstract class AppSpacing {
  static const xs = 6.0;
  static const sm = 10.0;
  static const md = 16.0;
  static const lg = 24.0;
  static const xl = 32.0;
}

abstract class AppRadii {
  static const sm = 10.0;
  static const md = 14.0;
  static const lg = 20.0;
  static const pill = 999.0;
}

ThemeData buildAppTheme() {
  return ThemeData(
    useMaterial3: true,
    brightness: Brightness.dark,
    scaffoldBackgroundColor: AppColors.canvas,
    colorScheme: const ColorScheme.dark(
      primary: AppColors.primary,
      surface: AppColors.surface,
      error: AppColors.error,
    ),
    appBarTheme: const AppBarTheme(
      backgroundColor: AppColors.canvas,
      foregroundColor: AppColors.textPrimary,
      elevation: 0,
      centerTitle: false,
    ),
    textTheme: const TextTheme(
      displayLarge: AppTypography.displayLarge,
      displayMedium: AppTypography.displayMedium,
      titleLarge: AppTypography.title,
      bodyLarge: AppTypography.body,
      bodySmall: AppTypography.bodySmall,
      labelSmall: AppTypography.mono,
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: AppColors.primary,
        foregroundColor: Colors.white,
        minimumSize: const Size.fromHeight(48),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadii.pill)),
        textStyle: const TextStyle(fontFamily: AppTypography.displayFamily, fontWeight: FontWeight.w700, fontSize: 15),
      ),
    ),
    bottomNavigationBarTheme: const BottomNavigationBarThemeData(
      backgroundColor: AppColors.canvas,
      selectedItemColor: AppColors.primary,
      unselectedItemColor: AppColors.textMuted,
      type: BottomNavigationBarType.fixed,
      selectedLabelStyle: AppTypography.monoSmall,
      unselectedLabelStyle: AppTypography.monoSmall,
    ),
  );
}
