package com.lxmusic.app.data.api

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
import java.util.concurrent.TimeUnit

/**
 * Retrofit 组装：baseUrl 固定为 https://yy.shangbaicn.com/
 * （Subsonic 接口路径为 /rest/*.view，由接口定义拼出）
 */
object NetworkModule {

    const val BASE_URL = "https://yy.shangbaicn.com/"

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

    fun createApi(session: SessionManager): SubsonicApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(session))
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val gson = GsonBuilder()
            .registerTypeAdapter(object : TypeToken<List<Song>>() {}.type, lenientList<Song>())
            .registerTypeAdapter(object : TypeToken<List<Album>>() {}.type, lenientList<Album>())
            .registerTypeAdapter(object : TypeToken<List<Artist>>() {}.type, lenientList<Artist>())
            .registerTypeAdapter(object : TypeToken<List<Playlist>>() {}.type, lenientList<Playlist>())
            .create()
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(SubsonicApi::class.java)
    }
}
