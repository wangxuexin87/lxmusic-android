package com.lxmusic.app.data.api

import com.lxmusic.app.data.model.SubsonicEnvelope
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Subsonic REST API（lxserver 已实现的子集）
 * 通用鉴权参数由 [AuthInterceptor] 自动追加：v / c / f / u / t / s
 */
interface SubsonicApi {

    /** 连通 & 登录校验 */
    @GET("ping.view")
    suspend fun ping(): SubsonicEnvelope

    /** 推荐/随机/最新专辑：type = random | newest | recent */
    @GET("getAlbumList2.view")
    suspend fun getAlbumList2(
        @Query("type") type: String = "random",
        @Query("size") size: Int = 30,
        @Query("offset") offset: Int = 0
    ): SubsonicEnvelope

    /** 搜索：query 支持 wy:/kg:/tx:/kw:/mg: 平台前缀，online:/local: 范围限定 */
    @GET("search3.view")
    suspend fun search3(
        @Query("query") query: String,
        @Query("songCount") songCount: Int = 30,
        @Query("albumCount") albumCount: Int = 10,
        @Query("artistCount") artistCount: Int = 10
    ): SubsonicEnvelope

    @GET("getAlbum.view")
    suspend fun getAlbum(@Query("id") id: String): SubsonicEnvelope

    @GET("getArtist.view")
    suspend fun getArtist(@Query("id") id: String): SubsonicEnvelope

    @GET("getSong.view")
    suspend fun getSong(@Query("id") id: String): SubsonicEnvelope

    @GET("getPlaylists.view")
    suspend fun getPlaylists(): SubsonicEnvelope

    @GET("getPlaylist.view")
    suspend fun getPlaylist(@Query("id") id: String): SubsonicEnvelope

    @GET("getStarred2.view")
    suspend fun getStarred2(): SubsonicEnvelope

    @GET("getLyricsBySongId.view")
    suspend fun getLyrics(@Query("id") id: String): SubsonicEnvelope

    /** 收藏 / 取消收藏 */
    @GET("star.view")
    suspend fun star(@Query("id") id: String): SubsonicEnvelope

    @GET("unstar.view")
    suspend fun unstar(@Query("id") id: String): SubsonicEnvelope

    /** 播放上报 */
    @GET("scrobble.view")
    suspend fun scrobble(@Query("id") id: String): SubsonicEnvelope
}
