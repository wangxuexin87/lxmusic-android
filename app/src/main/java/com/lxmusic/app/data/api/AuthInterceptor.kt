package com.lxmusic.app.data.api

import com.lxmusic.app.data.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 给每个 Subsonic 请求自动追加鉴权参数：
 * v=协议版本 c=客户端标识 f=json u=用户名 t=md5(密码+salt) s=salt
 *
 * 登录流程：先 [SessionManager.setCredentials] 存入账号，再调 ping 校验。
 */
class AuthInterceptor(private val session: SessionManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url.newBuilder()
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", CLIENT_NAME)
            .addQueryParameter("f", "json")
            .addQueryParameter("u", session.username)
            .addQueryParameter("t", session.token)
            .addQueryParameter("s", session.salt)
            .build()
        return chain.proceed(original.newBuilder().url(url).build())
    }

    companion object {
        const val CLIENT_NAME = "LXMusicAndroid"
        const val API_VERSION = "1.16.1"
    }
}
