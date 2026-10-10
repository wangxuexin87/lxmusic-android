package com.lxmusic.app.player

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.lxmusic.app.LxApp

/**
 * 后台播放服务：播放时自动转为前台服务并显示媒体通知，
 * 切后台 / 锁屏后继续播放。
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = (application as LxApp).playerManager.player
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
