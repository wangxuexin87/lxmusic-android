package com.lxmusic.app.data

import com.lxmusic.app.data.api.SubsonicApi
import com.lxmusic.app.data.model.Album
import com.lxmusic.app.data.model.Playlist
import com.lxmusic.app.data.model.Song

/**
 * 音乐数据仓库：把 Subsonic 响应的嵌套结构拍平成 App 用的列表。
 * 约定：status != "ok" 时抛 [ApiException]。
 */
class MusicRepository(
    private val api: SubsonicApi,
    val session: SessionManager
) {

    /** 登录：先存账号再 ping，失败则清空 */
    suspend fun login(username: String, password: String): Boolean {
        session.setCredentials(username, password)
        // 重试 3 次：热点刚连上时 DNS 可能还没好
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                ping()
                return true
            } catch (e: Exception) {
                lastError = e
                // 用户名密码错误不重试
                if ((e as? ApiException)?.code == 40) throw e
                if (attempt < 2) kotlinx.coroutines.delay(2000)
            }
        }
        session.clear()
        throw lastError!!
    }

    /** 用已存凭证 ping（登录态校验） */
    suspend fun ping() {
        checkOk(api.ping())
    }

    suspend fun randomAlbums(size: Int = 30): List<Album> {
        val r = checkOk(api.getAlbumList2(type = "random", size = size))
        return r.response?.albumList2?.album.orEmpty()
    }

    suspend fun newestAlbums(size: Int = 30): List<Album> {
        val r = checkOk(api.getAlbumList2(type = "newest", size = size))
        return r.response?.albumList2?.album.orEmpty()
    }

    suspend fun search(query: String): Triple<List<Song>, List<Album>, List<com.lxmusic.app.data.model.Artist>> {
        val r = checkOk(api.search3(query))
        val body = r.response?.searchResult3
        return Triple(
            body?.song.orEmpty(),
            body?.album.orEmpty(),
            body?.artist.orEmpty()
        )
    }

    suspend fun albumSongs(albumId: String): List<Song> {
        val r = checkOk(api.getAlbum(albumId))
        return r.response?.album?.song.orEmpty()
    }

    suspend fun playlists(): List<Playlist> {
        val r = checkOk(api.getPlaylists())
        return r.response?.playlists?.playlist.orEmpty()
    }

    /** 我的喜爱（收藏的歌曲） */
    suspend fun starredSongs(): List<Song> {
        val r = checkOk(api.getStarred2())
        return r.response?.starred2?.song.orEmpty()
    }

    suspend fun playlistSongs(playlistId: String): List<Song> {
        val r = checkOk(api.getPlaylist(playlistId))
        return r.response?.playlist?.entry.orEmpty()
    }

    suspend fun lyrics(songId: String): String? {
        val r = checkOk(api.getLyrics(songId))
        return r.response?.lyrics?.value
    }

    suspend fun scrobble(songId: String) {
        try { checkOk(api.scrobble(songId)) } catch (_: Exception) { /* 上报失败不打扰播放 */ }
    }

    /** 收藏 / 取消收藏 */
    suspend fun star(songId: String) {
        try { checkOk(api.star(songId)) } catch (_: Exception) { }
    }

    suspend fun unstar(songId: String) {
        try { checkOk(api.unstar(songId)) } catch (_: Exception) { }
    }

    /** 播放地址：直接给 ExoPlayer */
    fun streamUrl(songId: String): String =
        session.authedUrl("stream.view", mapOf("id" to songId))

    /** 封面地址：给 Coil */
    fun coverUrl(coverArtId: String?, size: Int? = null): String? {
        if (coverArtId.isNullOrEmpty()) return null
        val params = mutableMapOf("id" to coverArtId)
        if (size != null) params["size"] = size.toString()
        return session.authedUrl("getCoverArt.view", params)
    }

    private fun checkOk(envelope: com.lxmusic.app.data.model.SubsonicEnvelope): com.lxmusic.app.data.model.SubsonicEnvelope {
        val body = envelope.response ?: throw ApiException(-1, "空响应")
        if (body.status != "ok") {
            throw ApiException(body.error?.code ?: -1, body.error?.message ?: "未知错误")
        }
        return envelope
    }

    class ApiException(val code: Int, message: String) : Exception(message)
}
