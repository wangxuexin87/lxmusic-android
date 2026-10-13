package com.lxmusic.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.lxmusic.app.ui.NavGraph
import com.lxmusic.app.ui.theme.LxTheme
import com.lxmusic.app.ui.theme.isDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 6.0+ 申请存储权限（公共目录存歌用）
        if (android.os.Build.VERSION.SDK_INT in 23..28) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1001)
            }
        }
        val app = applicationContext as LxApp
        setContent {
            val mode by app.themeManager.mode.collectAsState()
            LxTheme(darkTheme = isDarkTheme(mode)) {
                CrashDialogIfNeeded()
                NavGraph()
            }
        }
    }

    // 方向盘/线控媒体键：上一曲、下一曲、播放暂停
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val app = applicationContext as LxApp
        val player = app.playerManager
        when (keyCode) {
            android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                try { player.next() } catch (_: Exception) {}
                return true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                try { player.previous() } catch (_: Exception) {}
                return true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY,
            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                try { player.toggle() } catch (_: Exception) {}
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
