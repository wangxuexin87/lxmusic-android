package com.lxmusic.app.data.api

import android.os.Build
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import com.lxmusic.app.data.SessionManager
import com.lxmusic.app.data.model.Album
import com.lxmusic.app.data.model.Artist
import com.lxmusic.app.data.model.Playlist
import com.lxmusic.app.data.model.Song
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.Type
import java.net.InetAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Retrofit 组装。baseUrl 由调用方传入（用户可在登录页修改服务器地址），
 * [com.lxmusic.app.LxApp] 会在地址变化时重建整个网络栈。
 * （Subsonic 接口路径形如 /rest/ping.view，由接口定义拼出）
 */
object NetworkModule {

    /**
     * Subsonic 服务端有时把单元素列表写成单个对象而非数组，
     * 这里做兼容：对象→包成单元素列表，null→空列表。
     */
    private inline fun <reified T> lenientList(): JsonDeserializer<List<T>> =
        JsonDeserializer { json: JsonElement, _: Type, ctx: JsonDeserializationContext ->
            when {
                json.isJsonArray -> json.asJsonArray.map { ctx.deserialize<T>(it, T::class.java) }
                json.isJsonNull -> emptyList()
                else -> listOf(ctx.deserialize(json, T::class.java))
            }
        }

    fun createApi(session: SessionManager, baseUrl: String, context: android.content.Context? = null): SubsonicApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val builder = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(session))
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)

        // Android 6.0 及以下：强制启用 TLS 1.2，否则 HTTPS 握手失败
        // 同时信任内置的 ISRG Root X1（Let's Encrypt）
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M && context != null) {
            try {
                val tlsFactory = Tls12SocketFactory(context)
                builder.sslSocketFactory(tlsFactory, tlsFactory.trustManager)
            } catch (_: Exception) { }
        }

        val client = builder.build()

        // Subsonic 接口挂在 /rest 下，baseUrl 统一补上
        val restUrl = baseUrl.trimEnd('/') + "/rest/"

        val gson = GsonBuilder()
            .registerTypeAdapter(object : TypeToken<List<Song>>() {}.type, lenientList<Song>())
            .registerTypeAdapter(object : TypeToken<List<Album>>() {}.type, lenientList<Album>())
            .registerTypeAdapter(object : TypeToken<List<Artist>>() {}.type, lenientList<Artist>())
            .registerTypeAdapter(object : TypeToken<List<Playlist>>() {}.type, lenientList<Playlist>())
            .create()
        return Retrofit.Builder()
            .baseUrl(restUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(SubsonicApi::class.java)
    }
}

/**
 * 老设备 TLS 1.2 启用器：Android 6.0 及以下默认不启用 TLS 1.2，
 * 导致 HTTPS 握手失败。这里强制启用 TLSv1.1/1.2。
 */
private class Tls12SocketFactory(
    private val context: android.content.Context
) : SSLSocketFactory() {
    private val delegate: SSLSocketFactory
    val trustManager: X509TrustManager

    init {
        // 系统证书 + 内置 ISRG Root X1
        val keyStore = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType())
        keyStore.load(null, null)
        // 先加系统默认的
        val defaultTmf = javax.net.ssl.TrustManagerFactory.getInstance(
            javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()
        )
        defaultTmf.init(null as java.security.KeyStore?)
        val defaultTm = defaultTmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
        for (cert in defaultTm.acceptedIssuers) {
            try { keyStore.setCertificateEntry(cert.subjectDN.name + System.nanoTime(), cert) } catch (_: Exception) {}
        }
        // 再加内置的 ISRG Root X1
        try {
            context.resources.openRawResource(com.lxmusic.app.R.raw.isrgrootx1).use { ins ->
                val cf = java.security.cert.CertificateFactory.getInstance("X.509")
                val ca = cf.generateCertificate(ins)
                keyStore.setCertificateEntry("isrgrootx1", ca)
            }
        } catch (_: Exception) {}
        val tmf = javax.net.ssl.TrustManagerFactory.getInstance(
            javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()
        )
        tmf.init(keyStore)
        trustManager = tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        delegate = ctx.socketFactory
    }

    private fun enableTls(socket: Socket?): Socket? {
        if (socket is SSLSocket) {
            socket.enabledProtocols = arrayOf("TLSv1.1", "TLSv1.2")
        }
        return socket
    }

    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites
    override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket? =
        enableTls(delegate.createSocket(s, host, port, autoClose))
    override fun createSocket(host: String?, port: Int): Socket? =
        enableTls(delegate.createSocket(host, port))
    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket? =
        enableTls(delegate.createSocket(host, port, localHost, localPort))
    override fun createSocket(host: InetAddress?, port: Int): Socket? =
        enableTls(delegate.createSocket(host, port))
    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket? =
        enableTls(delegate.createSocket(address, port, localAddress, localPort))
}
