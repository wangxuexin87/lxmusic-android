package com.lxmusic.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

/**
 * 登录会话：保存用户名+密码（加密存储），按 Subsonic 规范生成 salt/token。
 * token = md5(password + salt)，salt 每次登录重新生成。
 */
class SessionManager(context: Context) {

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "lx_session",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var username: String = prefs.getString(KEY_USER, "").orEmpty()
        private set
    private var password: String = prefs.getString(KEY_PASS, "").orEmpty()
    var salt: String = prefs.getString(KEY_SALT, "").orEmpty()
        private set

    val hasCredentials: Boolean get() = username.isNotEmpty() && password.isNotEmpty()

    /** token = md5(password + salt)，每次请求实时计算 */
    val token: String get() = md5(password + salt)

    fun setCredentials(user: String, pass: String) {
        username = user.trim()
        password = pass
        salt = randomSalt()
        prefs.edit()
            .putString(KEY_USER, username)
            .putString(KEY_PASS, password)
            .putString(KEY_SALT, salt)
            .apply()
    }

    fun clear() {
        username = ""
        password = ""
        salt = ""
        prefs.edit().clear().apply()
    }

    /**
     * 拼出带鉴权的完整 URL（给 ExoPlayer 的 stream / Coil 的 getCoverArt 用，
     * 这些不走 Retrofit，需要手拼参数）。
     */
    fun authedUrl(baseUrl: String, endpoint: String, extra: Map<String, String> = emptyMap()): String {
        val sb = StringBuilder(baseUrl.trimEnd('/')).append("/rest/").append(endpoint).append('?')
        val params = linkedMapOf(
            "v" to "1.16.1",
            "c" to "LXMusicAndroid",
            "f" to "json",
            "u" to username,
            "t" to token,
            "s" to salt
        )
        extra.forEach { (k, v) -> params[k] = v }
        sb.append(params.entries.joinToString("&") { (k, v) ->
            "${k}=${java.net.URLEncoder.encode(v, "UTF-8")}"
        })
        return sb.toString()
    }

    private fun randomSalt(): String {
        val bytes = ByteArray(8)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun md5(s: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_USER = "username"
        private const val KEY_PASS = "password"
        private const val KEY_SALT = "salt"
    }
}
