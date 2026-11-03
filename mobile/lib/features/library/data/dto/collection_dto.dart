class CollectionDto {
  const CollectionDto({required this.likedTracks, required this.followedArtists, required this.followedPlaylists, required this.followedShows, required this.savedAlbums, required this.ownedTracks, required this.downloadableTracks, required this.userPlaylists});
  final List<String> likedTracks; final List<String> followedArtists; final List<String> followedPlaylists; final List<String> followedShows; final List<String> savedAlbums; final List<String> ownedTracks; final List<String> downloadableTracks; final List<UserPlaylistDto> userPlaylists;
  factory CollectionDto.fromJson(Map<String, dynamic> j) => CollectionDto(likedTracks: (j['likedTracks'] as List).cast<String>(), followedArtists: (j['followedArtists'] as List).cast<String>(), followedPlaylists: (j['followedPlaylists'] as List).cast<String>(), followedShows: (j['followedShows'] as List).cast<String>(), savedAlbums: (j['savedAlbums'] as List).cast<String>(), ownedTracks: (j['ownedTracks'] as List).cast<String>(), downloadableTracks: (j['downloadableTracks'] as List? ?? []).cast<String>(), userPlaylists: (j['userPlaylists'] as List).map((e) => UserPlaylistDto.fromJson(e as Map<String, dynamic>)).toList());
}

class UserPlaylistDto {
  const UserPlaylistDto({required this.id, required this.title, required this.trackIds, required this.createdAt, this.description});
  final String id; final String title; final List<String> trackIds; final String createdAt; final String? description;
  factory UserPlaylistDto.fromJson(Map<String, dynamic> j) => UserPlaylistDto(id: j['id'] as String, title: j['title'] as String, trackIds: (j['trackIds'] as List).cast<String>(), createdAt: j['createdAt'] as String, description: j['description'] as String?);
}
