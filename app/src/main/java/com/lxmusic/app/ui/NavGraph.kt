package com.lxmusic.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lxmusic.app.LxApp
import com.lxmusic.app.ui.album.AlbumScreen
import com.lxmusic.app.ui.downloads.DownloadsScreen
import com.lxmusic.app.ui.home.HomeScreen
import com.lxmusic.app.ui.login.LoginScreen
import com.lxmusic.app.ui.player.MiniPlayer
import com.lxmusic.app.ui.player.PlayerScreen
import com.lxmusic.app.ui.playlists.PlaylistsScreen
import com.lxmusic.app.ui.profile.ProfileScreen
import com.lxmusic.app.ui.search.SearchScreen
import com.lxmusic.app.ui.theme.BrandGreen

object Routes {
    const val LOGIN = "login"
    const val MAIN = "main"
    const val PLAYER = "player"
    const val DOWNLOADS = "downloads"
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as LxApp
    val startRoute = Routes.LOGIN

    NavHost(navController = navController, startDestination = startRoute) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    // 正常登录成功，关掉离线模式
                    app.downloadManager.setOfflineMode(false)
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onOfflineMode = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.MAIN) {
            MainScaffold(
                onPlayerClick = { navController.navigate(Routes.PLAYER) },
                onDownloadsClick = { navController.navigate(Routes.DOWNLOADS) },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.PLAYER) {
            PlayerScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.DOWNLOADS) {
            val app = LocalContext.current.applicationContext as LxApp
            DownloadsScreen(
                onBack = { navController.popBackStack() },
                onPlay = { item ->
                    // 播放下载的歌曲：构造 Song 对象走正常播放流程（会自动用本地文件）
                    val song = com.lxmusic.app.data.model.Song(
                        id = item.songId,
                        title = item.title,
                        album = item.album,
                        artist = item.artist,
                        albumId = null,
                        artistId = null,
                        coverArt = item.coverArt,
                        duration = null,
                        track = null,
                        year = null,
                        genre = null,
                        size = null
                    )
                    app.playerManager.playList(listOf(song), 0)
                    navController.popBackStack()
                }
            )
        }
    }
}

@Composable
private fun MainScaffold(
    onPlayerClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onLogout: () -> Unit
) {
    val app = LocalContext.current.applicationContext as LxApp
    val offlineMode by app.downloadManager.offlineMode.collectAsState()
    // 用 key 隔离：离线切换时整个导航重建，避免 NavBackStackEntry 崩溃
    key(offlineMode) {
        MainScaffoldContent(
            offlineMode = offlineMode,
            onPlayerClick = onPlayerClick,
            onDownloadsClick = onDownloadsClick,
            onLogout = onLogout
        )
    }
}

@Composable
private fun MainScaffoldContent(
    offlineMode: Boolean,
    onPlayerClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onLogout: () -> Unit
) {
    val app = LocalContext.current.applicationContext as LxApp
    val ultraMinimal by app.themeManager.ultraMinimal.collectAsState()
    // 离线模式：只保留歌单和我的；极简模式：只保留搜索、歌单、我的（无主页）
    val tabs = if (offlineMode) {
        listOf(
            Triple("playlists", "歌单", Icons.Default.PlaylistPlay),
            Triple("profile", "我的", Icons.Default.Person)
        )
    } else if (ultraMinimal) {
        listOf(
            Triple("search", "搜索", Icons.Default.Search),
            Triple("playlists", "歌单", Icons.Default.PlaylistPlay),
            Triple("profile", "我的", Icons.Default.Person)
        )
    } else {
        listOf(
            Triple("home", "首页", Icons.Default.Home),
            Triple("search", "搜索", Icons.Default.Search),
            Triple("playlists", "歌单", Icons.Default.PlaylistPlay),
            Triple("profile", "我的", Icons.Default.Person)
        )
    }
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    // 离线默认进歌单（本地歌曲），极简默认进搜索
    val defaultRoute = if (offlineMode) "playlists" else if (ultraMinimal) "search" else "home"
    val current = backStack?.destination?.route ?: defaultRoute
    // 专辑详情是否打开：打开时隐藏底部导航的 tab 高亮，保持迷你播放条

    val currentSong by app.playerManager.currentSong.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                if (currentSong != null) {
                    MiniPlayer(onClick = onPlayerClick)
                }
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    tabs.forEach { (route, label, icon) ->
                        NavigationBarItem(
                            selected = current == route,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.startDestinationId)
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BrandGreen,
                                selectedTextColor = BrandGreen,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = Color(0xFF1A3A2E)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // key 隔离：模式切换时重建 NavHost，避免 BackStackEntry 销毁后访问 ViewModel
            val navKey = "$offlineMode-$ultraMinimal"
            androidx.compose.runtime.key(navKey) {
                NavHost(navController = navController, startDestination = defaultRoute) {
                composable("home") {
                    HomeScreen(
                        onAlbumClick = { id -> navController.navigate("album/$id") }
                    )
                }
                composable("search") {
                    SearchScreen(
                        onAlbumClick = { id -> navController.navigate("album/$id") }
                    )
                }
                composable("playlists") {
                    PlaylistsScreen()
                }
                composable("profile") {
                    ProfileScreen(
                        onLogout = onLogout,
                        onDownloadsClick = onDownloadsClick
                    )
                }
                composable(
                    "album/{albumId}",
                    arguments = listOf(navArgument("albumId") { type = NavType.StringType })
                ) { entry ->
                    val albumId = entry.arguments?.getString("albumId").orEmpty()
                    AlbumScreen(albumId = albumId, onBack = { navController.popBackStack() })
                }
            }
            } // key 隔离结束
        }
    }
}
