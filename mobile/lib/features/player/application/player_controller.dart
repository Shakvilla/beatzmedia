import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:just_audio/just_audio.dart';
import '../data/dto/stream_dto.dart';
import '../data/playback_repository.dart';
import '../../discovery/data/dto/catalog_dto.dart';

class PlayerState {
  const PlayerState(
      {this.track,
      this.queue = const [],
      this.position = Duration.zero,
      this.duration = Duration.zero,
      this.isPlaying = false,
      this.isBuffering = false,
      this.previewSeconds,
      this.error});
  final TrackDto? track;
  final List<TrackDto> queue;
  final Duration position;
  final Duration duration;
  final bool isPlaying;
  final bool isBuffering;
  final int? previewSeconds;
  final String? error;
  PlayerState copyWith(
          {TrackDto? track,
          List<TrackDto>? queue,
          Duration? position,
          Duration? duration,
          bool? isPlaying,
          bool? isBuffering,
          int? previewSeconds,
          String? error}) =>
      PlayerState(
          track: track ?? this.track,
          queue: queue ?? this.queue,
          position: position ?? this.position,
          duration: duration ?? this.duration,
          isPlaying: isPlaying ?? this.isPlaying,
          isBuffering: isBuffering ?? this.isBuffering,
          previewSeconds: previewSeconds ?? this.previewSeconds,
          error: error);
  bool get isPreview => previewSeconds != null;
}

class PlayerController extends StateNotifier<PlayerState> {
  PlayerController(this._repo) : super(const PlayerState()) {
    _player = AudioPlayer();
    _player.positionStream.listen((pos) {
      final preview = state.previewSeconds;
      if (preview != null && pos.inSeconds >= preview) {
        _player.pause();
        state = state.copyWith(
            position: Duration(seconds: preview), isPlaying: false);
        return;
      }
      state = state.copyWith(position: pos);
    });
    _player.durationStream.listen((d) {
      if (d != null) state = state.copyWith(duration: d);
    });
    _player.playerStateStream.listen((ps) => state = state.copyWith(
        isPlaying: ps.playing,
        isBuffering: ps.processingState == ProcessingState.buffering));
  }

  final PlaybackRepository _repo;
  late final AudioPlayer _player;
  String? _currentUrl;
  DateTime? _expiresAt;
  Timer? _expiryTimer;

  Future<void> playTrack(TrackDto track, {List<TrackDto>? queue}) async {
    state = state.copyWith(
        track: track,
        queue: queue ?? state.queue,
        isBuffering: true,
        error: null);
    try {
      final stream = await _repo.getStream(track.id);
      _currentUrl = stream.audioUrl;
      _expiresAt = DateTime.tryParse(stream.expiresAt);
      state = state.copyWith(previewSeconds: stream.previewSeconds);
      await _player.setUrl(stream.audioUrl);
      await _player.play();
      _scheduleExpiryRefresh();
      unawaited(_repo.recordPlay(track.id));
    } catch (e) {
      state = state.copyWith(isBuffering: false, error: e.toString());
    }
  }

  void _scheduleExpiryRefresh() {
    _expiryTimer?.cancel();
    if (_expiresAt == null) return;
    final refreshAt = _expiresAt!.subtract(const Duration(seconds: 10));
    final delay = refreshAt.difference(DateTime.now());
    if (delay.isNegative) return;
    _expiryTimer = Timer(delay, () async {
      final track = state.track;
      if (track == null) return;
      final pos = state.position;
      try {
        final stream = await _repo.getStream(track.id);
        await _player.setUrl(stream.audioUrl, initialPosition: pos);
        if (state.isPlaying) await _player.play();
        _currentUrl = stream.audioUrl;
        _expiresAt = DateTime.tryParse(stream.expiresAt);
        _scheduleExpiryRefresh();
      } catch (_) {}
    });
  }

  Future<void> playPause() async {
    if (_player.playing) {
      await _player.pause();
    } else {
      await _player.play();
    }
  }

  Future<void> seek(Duration pos) async => _player.seek(pos);
  Future<void> skipNext() async {
    final q = state.queue;
    final idx = q.indexWhere((t) => t.id == state.track?.id);
    if (idx >= 0 && idx + 1 < q.length) await playTrack(q[idx + 1]);
  }

  Future<void> skipPrevious() async {
    final q = state.queue;
    final idx = q.indexWhere((t) => t.id == state.track?.id);
    if (idx > 0) await playTrack(q[idx - 1]);
  }

  @override
  void dispose() {
    _expiryTimer?.cancel();
    _player.dispose();
    super.dispose();
  }
}

final playbackRepositoryProvider =
    Provider<PlaybackRepository>((ref) => PlaybackRepository());
final playerControllerProvider =
    StateNotifierProvider<PlayerController, PlayerState>(
        (ref) => PlayerController(ref.watch(playbackRepositoryProvider)));
