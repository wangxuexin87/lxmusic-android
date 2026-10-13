package com.lxmusic.app

import android.app.Application
import com.lxmusic.app.data.DownloadManager
import com.lxmusic.app.data.MusicRepository
import com.lxmusic.app.data.SessionManager
import com.lxmusic.app.data.ThemeManager
import com.lxmusic.app.data.api.NetworkModule
import com.lxmusic.app.data.api.SubsonicApi
import com.lxmusic.app.player.PlayerManager

/**
 * 手工 DI 容器：MVP 阶段不用 Hilt，保持简单。
 *
 * 服务器地址可在登录页修改；api/repository 会在地址变化时自动重建。
 */
class LxApp : Application() {

    val sessionManager by lazy { SessionManager(this) }

    val themeManager by lazy { ThemeManager(this) }

    val downloadManager by lazy { DownloadManager(this) }

    private var builtForUrl: String? = null
    private var _api: SubsonicApi? = null
    private var _repository: MusicRepository? = null

    val api: SubsonicApi get() = network().first
    val repository: MusicRepository get() = network().second

    private fun network(): Pair<SubsonicApi, MusicRepository> {
        val url = sessionManager.serverUrl
        if (_api == null || builtForUrl != url) {
            _api = NetworkModule.createApi(sessionManager, url, this)
            _repository = MusicRepository(_api!!, sessionManager)
            builtForUrl = url
        }
        return _api!! to _repository!!
    }

    val playerManager by lazy { PlayerManager(this) { repository } }

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        playerManager.release()
    }
}
