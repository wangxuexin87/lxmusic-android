# LXMusic Android（Subsonic 客户端脚手架）

对接 `https://yy.shangbaicn.com`（lxserver）的 Android 原生 App 最小可运行版本。

## 功能

- 登录（Subsonic 账号 = 网页/同步同一套用户名+密码，加密本地保存）
- 首页：随机推荐 / 最新专辑
- 搜索：支持 `wy:` / `kg:` / `tx:` / `kw:` / `mg:` 平台前缀
- 专辑详情 / 歌单列表 / 歌单详情
- 播放：ExoPlayer，后台播放 + 锁屏通知/控制
- 播放页：封面、进度条、歌词、切歌

## 技术栈

Kotlin + Jetpack Compose (Material3) + Media3 ExoPlayer + Retrofit/OkHttp +
Coroutines/Flow + Coil + EncryptedSharedPreferences。手工 DI（无 Hilt），保持脚手架轻量。

## 构建

1. 用 Android Studio 打开本目录（Gradle 会自动同步依赖）
2. 连接手机或启动模拟器，点 Run

> 服务器地址写死在 `data/api/NetworkModule.kt` 的 `BASE_URL`，
> 换服务器只改这一处。

## 目录结构

```
app/src/main/java/com/lxmusic/app/
├── MainActivity.kt / LxApp.kt        # 入口 & 手工 DI 容器
├── data/
│   ├── api/SubsonicApi.kt            # Retrofit 接口（/rest/*.view）
│   ├── api/AuthInterceptor.kt        # 自动追加 Subsonic 鉴权参数
│   ├── api/NetworkModule.kt          # Retrofit 组装，BASE_URL 在此
│   ├── model/Models.kt               # Subsonic DTO
│   ├── SessionManager.kt             # 凭证加密存储 + token/salt 生成
│   └── MusicRepository.kt            # 数据仓库（拍平嵌套响应）
├── player/
│   ├── PlayerManager.kt              # ExoPlayer 封装（应用级单例）
│   └── PlaybackService.kt            # MediaSessionService（后台保活）
└── ui/
    ├── NavGraph.kt                   # 导航：login → main → album/player
    ├── login/ / home/ / search/
    ├── playlists/ / album/ / player/  # 各页面 + ViewModel
    └── components/                   # SongRow / AlbumCard / MiniPlayer
```

## 后续可加

- Room 本地缓存（离线歌单）
- 下载到本地（`download.view`）
- 收藏/评分按钮（`star` / `setRating` 已在 Api 里）
- 均衡器、睡眠定时、桌面歌词
