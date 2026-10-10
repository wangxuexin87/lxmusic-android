package com.lxmusic.app

import android.app.Application
import com.lxmusic.app.data.MusicRepository
import com.lxmusic.app.data.SessionManager
import com.lxmusic.app.data.api.NetworkModule
import com.lxmusic.app.player.PlayerManager

/**
 * 手工 DI 容器：MVP 阶段不用 Hilt，保持简单。
 */
class LxApp : Application() {

    val sessionManager by lazy { SessionManager(this) }

    val api by lazy { NetworkModule.createApi(sessionManager) }

    val repository by lazy { MusicRepository(api, sessionManager) }

    val playerManager by lazy { PlayerManager(this, repository) }

    override fun onTerminate() {
        super.onTerminate()
        playerManager.release()
    }
}
