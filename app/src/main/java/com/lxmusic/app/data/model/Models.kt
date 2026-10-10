package com.lxmusic.app.data.model

import com.google.gson.annotations.SerializedName

/** Subsonic 响应外层：{"subsonic-response": {...}} */
data class SubsonicEnvelope(
    @SerializedName("subsonic-response") val response: SubsonicResponse?
)

data class SubsonicResponse(
    val status: String?,
    val version: String?,
    val error: SubsonicError?,
    val albumList2: AlbumList2?,
    val searchResult3: SearchResult3?,
    val album: AlbumDetail?,
    val artist: ArtistDetail?,
    val playlists: Playlists?,
    val playlist: PlaylistDetail?,
    val song: Song?,
    val lyrics: Lyrics?,
    val user: SubUser?
)

data class SubsonicError(
    val code: Int?,
    val message: String?
)

data class Song(
    val id: String?,
    val title: String?,
    val album: String?,
    val artist: String?,
    val albumId: String?,
    val artistId: String?,
    val coverArt: String?,
    val duration: Int?,      // 秒
    val track: Int?,
    val year: Int?,
    val genre: String?,
    val size: Long?
)

data class Album(
    val id: String?,
    val name: String?,
    val artist: String?,
    val artistId: String?,
    val coverArt: String?,
    val songCount: Int?,
    val duration: Int?,
    val year: Int?
)

data class AlbumDetail(
    val id: String?,
    val name: String?,
    val artist: String?,
    val coverArt: String?,
    val song: List<Song>?
)

data class AlbumList2(
    val album: List<Album>?
)

data class Artist(
    val id: String?,
    val name: String?,
    val coverArt: String?,
    val albumCount: Int?
)

data class ArtistDetail(
    val id: String?,
    val name: String?,
    val album: List<Album>?
)

data class SearchResult3(
    val song: List<Song>?,
    val album: List<Album>?,
    val artist: List<Artist>?
)

data class Playlist(
    val id: String?,
    val name: String?,
    val songCount: Int?,
    val coverArt: String?,
    val duration: Int?
)

data class Playlists(
    val playlist: List<Playlist>?
)

data class PlaylistDetail(
    val id: String?,
    val name: String?,
    val entry: List<Song>?
)

data class Lyrics(
    val artist: String?,
    val title: String?,
    val value: String?
)

data class SubUser(
    val username: String?
)
