import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../core/session/session_provider.dart';
import '../features/auth/presentation/screens/forgot_password_screen.dart';
import '../features/auth/presentation/screens/login_screen.dart';
import '../features/auth/presentation/screens/otp_verification_screen.dart';
import '../features/auth/presentation/screens/reset_password_screen.dart';
import '../features/auth/presentation/screens/signup_screen.dart';
import '../features/auth/presentation/screens/splash_screen.dart';
import '../features/auth/presentation/screens/welcome_screen.dart';
import '../features/commerce/presentation/screens/cart_screen.dart';
import '../features/commerce/presentation/screens/checkout_screen.dart';
import '../features/commerce/presentation/screens/receipt_screen.dart';
import '../features/discovery/presentation/screens/album_screen.dart';
import '../features/discovery/presentation/screens/artist_screen.dart';
import '../features/discovery/presentation/screens/charts_screen.dart';
import '../features/discovery/presentation/screens/home_screen.dart';
import '../features/discovery/presentation/screens/search_screen.dart';
import '../features/library/presentation/screens/downloads_screen.dart';
import '../features/library/presentation/screens/library_screen.dart';
import '../features/notifications/presentation/screens/notifications_screen.dart';
import '../features/onboarding/presentation/screens/genre_onboarding_screen.dart';
import '../features/player/presentation/screens/lyrics_screen.dart';
import '../features/player/presentation/screens/now_playing_screen.dart';
import '../features/podcasts/presentation/screens/podcast_detail_screen.dart';
import '../features/podcasts/presentation/screens/podcasts_screen.dart';
import '../features/settings/presentation/screens/settings_screen.dart';
import '../features/social/presentation/screens/friend_activity_screen.dart';
import '../features/social/presentation/screens/live_session_screen.dart';
import '../features/store/presentation/screens/store_screen.dart';

bool _isSafePath(String path) {
  if (!path.startsWith('/')) return false;
  if (path.contains('://') || path.contains('//')) return false;
  return true;
}

final appRouterProvider = Provider<GoRouter>((ref) {
  final router = GoRouter(
    initialLocation: '/',
    redirect: (context, state) {
      final loc = state.matchedLocation;
      if (!_isSafePath(loc)) return '/';
      final isSplash = loc == '/';
      const publicPaths = {
        '/',
        '/welcome',
        '/login',
        '/signup',
        '/forgot-password',
        '/otp',
        '/reset-password',
        '/home',
        '/search',
        '/charts',
        '/store',
        '/podcasts',
      };
      final isPublic = publicPaths.contains(loc) ||
          loc.startsWith('/artist/') ||
          loc.startsWith('/album/') ||
          loc.startsWith('/podcasts/');
      final status = ref.read(sessionProvider).status;
      if (status == SessionStatus.restoring && !isSplash) return '/';
      if (status == SessionStatus.unauthenticated && !isPublic) return '/login';
      return null;
    },
    routes: [
      GoRoute(
          path: '/',
          name: 'splash',
          builder: (context, state) => const SplashScreen()),
      GoRoute(
          path: '/welcome',
          name: 'welcome',
          builder: (context, state) => const WelcomeScreen()),
      GoRoute(
          path: '/login',
          name: 'login',
          builder: (context, state) => const LoginScreen()),
      GoRoute(
          path: '/signup',
          name: 'signup',
          builder: (context, state) => const SignupScreen()),
      GoRoute(
          path: '/forgot-password',
          name: 'forgot-password',
          builder: (context, state) => const ForgotPasswordScreen()),
      GoRoute(
          path: '/otp',
          name: 'otp',
          builder: (context, state) => OtpVerificationScreen(
              email:
                  state.uri.queryParameters['email'] ?? 'your email address')),
      GoRoute(
          path: '/reset-password',
          name: 'reset-password',
          builder: (context, state) {
            final token = state.uri.queryParameters['token'] ?? '';
            return ResetPasswordScreen(token: token);
          }),
      GoRoute(
          path: '/onboarding',
          name: 'onboarding',
          builder: (context, state) => const GenreOnboardingScreen()),
      GoRoute(
          path: '/home',
          name: 'home',
          builder: (context, state) => const HomeScreen()),
      GoRoute(
          path: '/search',
          name: 'search',
          builder: (context, state) => const SearchScreen()),
      GoRoute(
          path: '/charts',
          name: 'charts',
          builder: (context, state) => const ChartsScreen()),
      GoRoute(
          path: '/artist/:id',
          name: 'artist',
          builder: (context, state) =>
              ArtistScreen(artistId: state.pathParameters['id']!)),
      GoRoute(
          path: '/album/:id',
          name: 'album',
          builder: (context, state) =>
              AlbumScreen(albumId: state.pathParameters['id']!)),
      GoRoute(
          path: '/player',
          name: 'player',
          builder: (context, state) => const NowPlayingScreen()),
      GoRoute(
          path: '/lyrics/:id',
          name: 'lyrics',
          builder: (context, state) =>
              LyricsScreen(trackId: state.pathParameters['id']!)),
      GoRoute(
          path: '/library',
          name: 'library',
          builder: (context, state) => const LibraryScreen()),
      GoRoute(
          path: '/downloads',
          name: 'downloads',
          builder: (context, state) => const DownloadsScreen()),
      GoRoute(
          path: '/cart',
          name: 'cart',
          builder: (context, state) => const CartScreen()),
      GoRoute(
          path: '/checkout',
          name: 'checkout',
          builder: (context, state) => const CheckoutScreen()),
      GoRoute(
          path: '/receipt/:id',
          name: 'receipt',
          builder: (context, state) =>
              ReceiptScreen(orderId: state.pathParameters['id']!)),
      GoRoute(
          path: '/podcasts',
          name: 'podcasts',
          builder: (context, state) => const PodcastsScreen()),
      GoRoute(
          path: '/podcasts/:id',
          name: 'podcast-detail',
          builder: (context, state) =>
              PodcastDetailScreen(podcastId: state.pathParameters['id']!)),
      GoRoute(
          path: '/store',
          name: 'store',
          builder: (context, state) => const StoreScreen()),
      GoRoute(
          path: '/notifications',
          name: 'notifications',
          builder: (context, state) => const NotificationsScreen()),
      GoRoute(
          path: '/settings',
          name: 'settings',
          builder: (context, state) => const SettingsScreen()),
      GoRoute(
          path: '/friend-activity',
          name: 'friend-activity',
          builder: (context, state) => const FriendActivityScreen()),
      GoRoute(
          path: '/live-session',
          name: 'live-session',
          builder: (context, state) => const LiveSessionScreen()),
    ],
    errorBuilder: (context, state) =>
        Scaffold(body: Center(child: Text('Route not found: ${state.uri}'))),
  );
  ref.onDispose(router.dispose);
  return router;
});

final appRouter = GoRouter(
  initialLocation: '/',
  routes: [
    GoRoute(
        path: '/',
        name: 'splash',
        builder: (context, state) => const SplashScreen()),
    GoRoute(
        path: '/welcome',
        name: 'welcome',
        builder: (context, state) => const WelcomeScreen()),
    GoRoute(
        path: '/login',
        name: 'login',
        builder: (context, state) => const LoginScreen()),
    GoRoute(
        path: '/signup',
        name: 'signup',
        builder: (context, state) => const SignupScreen()),
    GoRoute(
        path: '/forgot-password',
        name: 'forgot-password',
        builder: (context, state) => const ForgotPasswordScreen()),
    GoRoute(
        path: '/otp',
        name: 'otp',
        builder: (context, state) => OtpVerificationScreen(
            email: state.uri.queryParameters['email'] ?? 'your email address')),
    GoRoute(
        path: '/reset-password',
        name: 'reset-password',
        builder: (context, state) {
          final token = state.uri.queryParameters['token'] ?? '';
          return ResetPasswordScreen(token: token);
        }),
    GoRoute(
        path: '/onboarding',
        name: 'onboarding',
        builder: (context, state) => const GenreOnboardingScreen()),
    GoRoute(
        path: '/home',
        name: 'home',
        builder: (context, state) => const HomeScreen()),
    GoRoute(
        path: '/search',
        name: 'search',
        builder: (context, state) => const SearchScreen()),
    GoRoute(
        path: '/charts',
        name: 'charts',
        builder: (context, state) => const ChartsScreen()),
    GoRoute(
        path: '/artist/:id',
        name: 'artist',
        builder: (context, state) =>
            ArtistScreen(artistId: state.pathParameters['id']!)),
    GoRoute(
        path: '/album/:id',
        name: 'album',
        builder: (context, state) =>
            AlbumScreen(albumId: state.pathParameters['id']!)),
    GoRoute(
        path: '/player',
        name: 'player',
        builder: (context, state) => const NowPlayingScreen()),
    GoRoute(
        path: '/lyrics/:id',
        name: 'lyrics',
        builder: (context, state) =>
            LyricsScreen(trackId: state.pathParameters['id']!)),
    GoRoute(
        path: '/library',
        name: 'library',
        builder: (context, state) => const LibraryScreen()),
    GoRoute(
        path: '/downloads',
        name: 'downloads',
        builder: (context, state) => const DownloadsScreen()),
    GoRoute(
        path: '/cart',
        name: 'cart',
        builder: (context, state) => const CartScreen()),
    GoRoute(
        path: '/checkout',
        name: 'checkout',
        builder: (context, state) => const CheckoutScreen()),
    GoRoute(
        path: '/receipt/:id',
        name: 'receipt',
        builder: (context, state) =>
            ReceiptScreen(orderId: state.pathParameters['id']!)),
    GoRoute(
        path: '/podcasts',
        name: 'podcasts',
        builder: (context, state) => const PodcastsScreen()),
    GoRoute(
        path: '/podcasts/:id',
        name: 'podcast-detail',
        builder: (context, state) =>
            PodcastDetailScreen(podcastId: state.pathParameters['id']!)),
    GoRoute(
        path: '/store',
        name: 'store',
        builder: (context, state) => const StoreScreen()),
    GoRoute(
        path: '/notifications',
        name: 'notifications',
        builder: (context, state) => const NotificationsScreen()),
    GoRoute(
        path: '/settings',
        name: 'settings',
        builder: (context, state) => const SettingsScreen()),
    GoRoute(
        path: '/friend-activity',
        name: 'friend-activity',
        builder: (context, state) => const FriendActivityScreen()),
    GoRoute(
        path: '/live-session',
        name: 'live-session',
        builder: (context, state) => const LiveSessionScreen()),
  ],
);
