package com.lxmusic.app.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.lxmusic.app.LxApp
import com.lxmusic.app.R

/**
 * 后台播放服务：播放时自动转为前台服务并显示媒体通知，
 * 切后台 / 锁屏后继续播放。
 *
 * 注意：startForegroundService() 启动后必须在 5 秒内调 startForeground()，
 * 否则系统会抛 RemoteServiceException 杀掉 App（Android 8+）。
 * 这里在 onStartCommand 里立即保活，Media3 后续会自动更新通知内容。
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = try {
            (application as LxApp).playerManager.player
        } catch (_: Exception) {
            null
        }
        mediaSession = player?.let { MediaSession.Builder(this, it).build() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // 立即进入前台，避免 5 秒超时被系统杀掉
        try {
            startForeground(NOTIFICATION_ID, buildPlaceholderNotification())
        } catch (_: Exception) { }
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) { }
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun buildPlaceholderNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "正在播放",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LX Music")
            .setContentText("正在准备播放…")
            .setSmallIcon(R.drawable.ic_stat_music_note)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "lx_playback"
    }
}
