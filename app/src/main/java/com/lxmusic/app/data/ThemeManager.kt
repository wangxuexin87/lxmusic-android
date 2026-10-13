package com.lxmusic.app.data

import android.content.Context
import com.lxmusic.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 主题设置：深色 / 浅色 / 自动（6:00-18:00 浅色，其余深色）
 * 保存在普通 SharedPreferences，随 App 重启保留。
 */
class ThemeManager(context: Context) {

    private val prefs = context.getSharedPreferences("lx_theme", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(
        try {
            ThemeMode.valueOf(prefs.getString(KEY_MODE, ThemeMode.DARK.name) ?: ThemeMode.DARK.name)
        } catch (_: Exception) {
            ThemeMode.DARK
        }
    )
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun setMode(mode: ThemeMode) {
        _mode.value = mode
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    /** 迷你栏显示当前歌词 */
    private val _miniLyric = MutableStateFlow(prefs.getBoolean(KEY_MINI_LYRIC, true))
    val miniLyric: StateFlow<Boolean> = _miniLyric.asStateFlow()
    fun setMiniLyric(v: Boolean) {
        _miniLyric.value = v
        prefs.edit().putBoolean(KEY_MINI_LYRIC, v).apply()
    }

    /** 迷你栏透明 */
    private val _miniTransparent = MutableStateFlow(prefs.getBoolean(KEY_MINI_TRANS, false))
    val miniTransparent: StateFlow<Boolean> = _miniTransparent.asStateFlow()
    fun setMiniTransparent(v: Boolean) {
        _miniTransparent.value = v
        prefs.edit().putBoolean(KEY_MINI_TRANS, v).apply()
    }

    /** 低配模式：低内存设备优化（小图、降频、无动画） */
    private val _lowSpec = MutableStateFlow(prefs.getBoolean(KEY_LOW_SPEC, false))
    val lowSpec: StateFlow<Boolean> = _lowSpec.asStateFlow()
    fun setLowSpec(v: Boolean) {
        _lowSpec.value = v
        prefs.edit().putBoolean(KEY_LOW_SPEC, v).apply()
    }

    /** 极简模式：无图片、无主页、纯文字列表、静态歌词，极致流畅 */
    private val _ultraMinimal = MutableStateFlow(prefs.getBoolean(KEY_ULTRA_MINIMAL, false))
    val ultraMinimal: StateFlow<Boolean> = _ultraMinimal.asStateFlow()
    fun setUltraMinimal(v: Boolean) {
        _ultraMinimal.value = v
        prefs.edit().putBoolean(KEY_ULTRA_MINIMAL, v).apply()
    }

    companion object {
        private const val KEY_MODE = "theme_mode"
        private const val KEY_MINI_LYRIC = "mini_lyric"
        private const val KEY_MINI_TRANS = "mini_transparent"
        private const val KEY_LOW_SPEC = "low_spec_mode"
        private const val KEY_ULTRA_MINIMAL = "ultra_minimal_mode"
    }
}
