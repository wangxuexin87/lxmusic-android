package com.lxmusic.app.data

import android.content.Context
import com.lxmusic.app.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 下载的歌曲信息
 */
data class DownloadedSong(
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverArt: String?,
    val sizeBytes: Long,
    val fileBaseName: String = "" // 歌曲文件名（不含扩展名），用于重装后找封面
)

/**
 * 本地下载管理：歌曲 MP3 + 歌词 LRC 存到 App 专属外部存储（无需权限）。
 * 文件名用 songId，避免特殊字符问题。
 */
class DownloadManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("lx_downloads", Context.MODE_PRIVATE)

    /** 公共目录：卸载 App 后歌曲保留 */
    private val publicBase: File =
        File(android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_MUSIC), "LXMusic").apply { mkdirs() }
    private val musicDir: File = File(publicBase, "").apply { mkdirs() }
    private val lyricsDir: File = File(publicBase, "lyrics").apply { mkdirs() }
    private val coverDir: File = File(publicBase, "covers").apply { mkdirs() }

    init {
        // 迁移老版本私有目录的歌到公共目录（只跑一次）
        migrateFromPrivate()
    }

    /** 把旧版 App 私有目录的文件搬到公共目录 */
    private fun migrateFromPrivate() {
        if (prefs.getBoolean("migrated_to_public", false)) return
        try {
            val oldMusic = appContext.getExternalFilesDir("music")
            val oldLyrics = appContext.getExternalFilesDir("lyrics")
            val oldCovers = appContext.getExternalFilesDir("covers")
            oldMusic?.listFiles()?.forEach { f ->
                val dest = File(musicDir, f.name)
                if (!dest.exists()) f.copyTo(dest)
            }
            oldLyrics?.listFiles()?.forEach { f ->
                val dest = File(lyricsDir, f.name)
                if (!dest.exists()) f.copyTo(dest)
            }
            oldCovers?.listFiles()?.forEach { f ->
                val dest = File(coverDir, f.name)
                if (!dest.exists()) f.copyTo(dest)
            }
            prefs.edit().putBoolean("migrated_to_public", true).apply()
        } catch (_: Exception) { }
    }

    private val httpClient = OkHttpClient()

    private val _downloads = MutableStateFlow<List<DownloadedSong>>(emptyList())
    val downloads: StateFlow<List<DownloadedSong>> = _downloads.asStateFlow()

    /** 正在下载中的 songId 集合（用于 UI 显示进度） */
    private val _downloading = MutableStateFlow<Set<String>>(emptySet())
    val downloading: StateFlow<Set<String>> = _downloading.asStateFlow()

    /** 边听边下载开关 */
    var autoDownload: Boolean
        get() = prefs.getBoolean(KEY_AUTO, false)
        set(v) { prefs.edit().putBoolean(KEY_AUTO, v).apply() }

    /** 离线模式：只播放本地已下载歌曲，不访问网络 */
    private val _offlineMode = MutableStateFlow(prefs.getBoolean(KEY_OFFLINE, false))
    val offlineMode: StateFlow<Boolean> = _offlineMode.asStateFlow()
    fun setOfflineMode(v: Boolean) {
        _offlineMode.value = v
        prefs.edit().putBoolean(KEY_OFFLINE, v).apply()
    }

    init {
        refreshList()
    }

    /** 手动刷新列表（打开下载页时调用，确保文件系统变化能反映） */
    fun refresh() {
        refreshList()
    }

    /** 歌曲是否已下载 */
    fun isDownloaded(songId: String?): Boolean {
        if (songId.isNullOrEmpty()) return false
        if (songFile(songId).exists()) return true
        // 重装后映射丢失，按下载列表里的 songId 兜底
        return _downloads.value.any { it.songId == songId }
    }

    /** 按歌手+歌名判断是否已下载（重装后 songId 对不上时用） */
    fun isDownloadedByName(artist: String?, title: String?): Boolean {
        if (title.isNullOrBlank()) return false
        val a = artist?.trim().orEmpty()
        val t = title.trim()
        return _downloads.value.any { d ->
            d.title.trim() == t && (a.isEmpty() || d.artist.trim() == a || d.artist.isEmpty())
        }
    }

    /** 本地歌曲文件（不存在返回 null） */
    fun localSongFile(songId: String?): File? {
        if (songId.isNullOrEmpty()) return null
        val f = songFile(songId)
        if (f.exists()) return f
        // 重装后映射丢失，从下载列表按 songId 找文件名
        val baseName = _downloads.value.find { it.songId == songId }?.fileBaseName
        if (!baseName.isNullOrBlank()) {
            val f2 = File(musicDir, "$baseName.mp3")
            if (f2.exists()) return f2
        }
        return null
    }

    /** 按歌手+歌名找本地文件（重装后 songId 对不上时用） */
    fun localSongFileByName(artist: String?, title: String?): File? {
        if (title.isNullOrBlank()) return null
        val a = artist?.trim().orEmpty()
        val t = title.trim()
        val match = _downloads.value.find { d ->
            d.title.trim() == t && (a.isEmpty() || d.artist.trim() == a || d.artist.isEmpty())
        } ?: return null
        val baseName = match.fileBaseName.ifBlank { null } ?: return null
        val f = File(musicDir, "$baseName.mp3")
        return if (f.exists()) f else null
    }

    /** 本地歌词文件（不存在返回 null） */
    fun localLyricsFile(songId: String?): File? {
        if (songId.isNullOrEmpty()) return null
        val f = lyricsFile(songId)
        return if (f.exists()) f else null
    }

    fun localLyricsText(songId: String?): String? {
        return try {
            localLyricsFile(songId)?.readText()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 下载歌曲 + 歌词。返回 true 表示成功。
     * @param streamUrl 带鉴权的 stream.view 地址
     * @param lyricsText 歌词文本（可空，有就一起存）
     */
    suspend fun download(song: Song, streamUrl: String, lyricsText: String?, coverUrl: String? = null): Boolean {
        val songId = song.id ?: return false
        if (songId in _downloading.value) return false
        _downloading.value = _downloading.value + songId
        try {
            val ok = withContext(Dispatchers.IO) {
                // 1. 下载音频
                val req = Request.Builder().url(streamUrl).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext false
                    val body = resp.body ?: return@withContext false
                    val tmp = File(musicDir, "$songId.tmp")
                    tmp.outputStream().use { out ->
                        body.byteStream().copyTo(out)
                    }
                    // 简单校验：文件太小可能是错误页面
                    if (tmp.length() < 1024) {
                        tmp.delete()
                        return@withContext false
                    }
                    // 文件名用"歌手 - 歌名"，存映射
                    val fileName = displayFileName(song)
                    val dest = File(musicDir, fileName)
                    if (dest.exists()) dest.delete()
                    // 删掉老版 songId.mp3（如果存在且名字不同）
                    val oldDest = File(musicDir, "$songId.mp3")
                    if (oldDest.exists() && oldDest.name != fileName) oldDest.delete()
                    if (tmp.renameTo(dest)) {
                        prefs.edit().putString("filename_$songId", fileName).apply()
                    } else {
                        return@withContext false
                    }
                }
                // 2. 存歌词
                if (!lyricsText.isNullOrBlank()) {
                    try {
                        lyricsFile(songId).writeText(lyricsText)
                    } catch (_: Exception) { }
                }
                // 2.5 下载封面（和歌曲同名，重装也能对上）
                if (!coverUrl.isNullOrBlank()) {
                    try {
                        val req = Request.Builder().url(coverUrl).build()
                        httpClient.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val body = resp.body
                                if (body != null) {
                                    val baseName = displayFileName(song).substringBeforeLast(".")
                                    val dest = coverFileByBaseName(baseName)
                                    if (dest.exists()) dest.delete()
                                    dest.outputStream().use { out ->
                                        body.byteStream().copyTo(out)
                                    }
                                    // 太小可能是错误页面
                                    if (dest.length() < 512) dest.delete()
                                }
                            }
                        }
                    } catch (_: Exception) { }
                }
                // 3. 写索引
                saveIndex(
                    DownloadedSong(
                        songId = songId,
                        title = song.title.orEmpty(),
                        artist = song.artist.orEmpty(),
                        album = song.album.orEmpty(),
                        coverArt = song.coverArt,
                        sizeBytes = songFile(songId).length(),
                        fileBaseName = displayFileName(song).substringBeforeLast(".")
                    )
                )
                true
            }
            if (ok) refreshList()
            return ok
        } catch (_: Exception) {
            return false
        } finally {
            _downloading.value = _downloading.value - songId
        }
    }

    /** 仅下载/更新歌词（边听边存歌词用） */
    suspend fun saveLyrics(songId: String?, lyricsText: String?) {
        if (songId.isNullOrEmpty() || lyricsText.isNullOrBlank()) return
        withContext(Dispatchers.IO) {
            try {
                lyricsFile(songId).writeText(lyricsText)
            } catch (_: Exception) { }
        }
    }

    /** 删除下载：歌曲 + 歌词一起删 */
    fun delete(songId: String): Boolean {
        return try {
            songFile(songId).delete()
            lyricsFile(songId).delete()
            coverFile(songId).delete()
            removeIndex(songId)
            refreshList()
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 已下载总大小 */
    fun totalSize(): Long {
        return try {
            (musicDir.listFiles()?.sumOf { it.length() } ?: 0L) +
                (lyricsDir.listFiles()?.sumOf { it.length() } ?: 0L)
        } catch (_: Exception) {
            0L
        }
    }

    /** 文件名：歌手 - 歌名，过滤非法字符 */
    private fun displayFileName(song: Song): String {
        val artist = (song.artist ?: "未知歌手").replace(Regex("[/\\\\:*?\"<>|]"), "_").take(50)
        val title = (song.title ?: song.id ?: "未知").replace(Regex("[/\\\\:*?\"<>|]"), "_").take(80)
        return "$artist - $title.mp3"
    }

    /** songId -> 文件名 映射（存 prefs，兼容老版 songId.mp3） */
    private fun savedFileName(songId: String): String? =
        prefs.getString("filename_$songId", null)

    private fun songFile(songId: String): File {
        val name = savedFileName(songId) ?: "$songId.mp3"
        return File(musicDir, name)
    }
    private fun lyricsFile(songId: String) = File(lyricsDir, "$songId.lrc")
    fun coverFile(songId: String) = File(coverDir, "$songId.jpg")

    /** 按歌曲文件名找封面（新命名：和歌曲同名） */
    fun coverFileByBaseName(baseName: String) = File(coverDir, "$baseName.jpg")

    /** 找本地封面：先查新命名（和歌曲同名），再查老命名（songId） */
    fun findCoverFile(songId: String?, baseName: String?): File? {
        if (!baseName.isNullOrBlank()) {
            val f = coverFileByBaseName(baseName)
            if (f.exists()) return f
        }
        if (!songId.isNullOrBlank()) {
            val f = coverFile(songId)
            if (f.exists()) return f
        }
        return null
    }

    private fun refreshList() {
        _downloads.value = loadIndex()
    }

    private fun loadIndex(): List<DownloadedSong> {
        // 先从 prefs 读元数据（标题/歌手等）
        val metaMap: Map<String, DownloadedSong> = try {
            val json = prefs.getString(KEY_INDEX, "[]") ?: "[]"
            val arr = JSONArray(json)
            (0 until arr.length()).associate { i ->
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                id to DownloadedSong(
                    songId = id,
                    title = o.optString("title"),
                    artist = o.optString("artist"),
                    album = o.optString("album"),
                    coverArt = o.optString("coverArt").ifEmpty { null },
                    sizeBytes = o.optLong("size")
                )
            }
        } catch (_: Exception) {
            emptyMap()
        }
        // 以文件存在为准扫描目录（索引丢失也能恢复）
        return try {
            // 建反向映射：文件名 -> songId
            val reverseMap = mutableMapOf<String, String>()
            for ((k, v) in prefs.all) {
                if (k.startsWith("filename_") && v is String) {
                    reverseMap[v] = k.removePrefix("filename_")
                }
            }
            val files = musicDir.listFiles { f -> f.isFile && f.extension == "mp3" }
                ?: return emptyList()
            files.mapNotNull { file ->
                val baseName = file.nameWithoutExtension
                // 新命名用反向映射找 songId，老命名直接就是 songId
                val songId = reverseMap[file.name] ?: baseName
                // 优先用索引里的元数据，否则从文件名解析兜底（重装后索引丢失也能显示）
                val meta = metaMap[songId]
                if (meta != null) {
                    meta.copy(sizeBytes = file.length(), fileBaseName = baseName)
                } else {
                    // 从"歌手 - 歌名"解析，解析不出就用文件名当标题
                    val parts = baseName.split(" - ", limit = 2)
                    val (artist, title) = if (parts.size == 2) parts[0] to parts[1] else "" to baseName
                    DownloadedSong(
                        songId = songId,
                        title = title,
                        artist = artist,
                        album = "",
                        coverArt = null,
                        sizeBytes = file.length(),
                        fileBaseName = baseName
                    )
                }
            }.sortedByDescending { it.sizeBytes } // 简单排序，新的在前（按大小近似）
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveIndex(song: DownloadedSong) {
        try {
            val list = loadIndex().toMutableList()
            list.removeAll { it.songId == song.songId }
            list.add(0, song)
            val arr = JSONArray()
            list.forEach {
                arr.put(
                    JSONObject()
                        .put("id", it.songId)
                        .put("title", it.title)
                        .put("artist", it.artist)
                        .put("album", it.album)
                        .put("coverArt", it.coverArt ?: "")
                        .put("size", it.sizeBytes)
                )
            }
            prefs.edit().putString(KEY_INDEX, arr.toString()).apply()
        } catch (_: Exception) { }
    }

    private fun removeIndex(songId: String) {
        try {
            val list = loadIndex().toMutableList()
            list.removeAll { it.songId == songId }
            val arr = JSONArray()
            list.forEach {
                arr.put(
                    JSONObject()
                        .put("id", it.songId)
                        .put("title", it.title)
                        .put("artist", it.artist)
                        .put("album", it.album)
                        .put("coverArt", it.coverArt ?: "")
                        .put("size", it.sizeBytes)
                )
            }
            prefs.edit().putString(KEY_INDEX, arr.toString()).apply()
        } catch (_: Exception) { }
    }

    companion object {
        private const val KEY_INDEX = "download_index"
        private const val KEY_AUTO = "auto_download"
        private const val KEY_OFFLINE = "offline_mode"
    }
}

/** 格式化文件大小 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.size - 1) {
        v /= 1024
        i++
    }
    return if (i == 0) "${v.toInt()} ${units[i]}"
    else String.format("%.1f %s", v, units[i])
}
