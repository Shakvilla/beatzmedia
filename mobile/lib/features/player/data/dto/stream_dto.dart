class StreamResponse {
  const StreamResponse(
      {required this.audioUrl, required this.expiresAt, this.previewSeconds});
  final String audioUrl;
  final String expiresAt;
  final int? previewSeconds;
  factory StreamResponse.fromJson(Map<String, dynamic> j) => StreamResponse(
      audioUrl: j['audioUrl'] as String,
      expiresAt: j['expiresAt'] as String,
      previewSeconds: (j['previewSeconds'] as num?)?.toInt());
}

class LyricsLine {
  const LyricsLine({required this.time, required this.text});
  final int? time;
  final String text;
  factory LyricsLine.fromJson(Map<String, dynamic> j) =>
      LyricsLine(time: (j['time'] as num?)?.toInt(), text: j['text'] as String);
}

class LyricsResponse {
  const LyricsResponse({required this.lines});
  final List<LyricsLine> lines;
  factory LyricsResponse.fromJson(Map<String, dynamic> j) => LyricsResponse(
      lines: (j['lines'] as List)
          .map((e) => LyricsLine.fromJson(e as Map<String, dynamic>))
          .toList());
  static LyricsResponse empty() => const LyricsResponse(lines: []);
}
