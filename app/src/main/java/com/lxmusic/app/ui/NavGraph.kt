package com.lxmusic.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lxmusic.app.LxApp
import com.lxmusic.app.ui.album.AlbumScreen
import com.lxmusic.app.ui.home.HomeScreen
import com.lxmusic.app.ui.login.LoginScreen
import com.lxmusic.app.ui.player.MiniPlayer
import com.lxmusic.app.ui.player.PlayerScreen
import com.lxmusic.app.ui.playlists.PlaylistsScreen
import com.lxmusic.app.ui.search.SearchScreen

object Routes {
    const val LOGIN = "login"
    const val MAIN = "main"
    const val ALBUM = "album/{albumId}"
    const val PLAYER = "player"

    fun album(albumId: String) = "album/$albumId"
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as LxApp
    val startRoute = Routes.LOGIN

    NavHost(navController = navController, startDestination = startRoute) {
        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = {
                navController.navigate(Routes.MAIN) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            })
        }
        composable(Routes.MAIN) {
            MainScaffold(
                onAlbumClick = { id -> navController.navigate(Routes.album(id)) },
                onPlayerClick = { navController.navigate(Routes.PLAYER) }
            )
        }
        composable(
            Routes.ALBUM,
            arguments = listOf(navArgument("albumId") { type = NavType.StringType })
        ) { entry ->
            val albumId = entry.arguments?.getString("albumId").orEmpty()
            AlbumScreen(
                albumId = albumId,
                onPlay = { navController.navigate(Routes.PLAYER) }
            )
        }
        composable(Routes.PLAYER) {
            PlayerScreen()
        }
    }
}

@Composable
private fun MainScaffold(
    onAlbumClick: (String) -> Unit,
    onPlayerClick: () -> Unit
) {
    val tabs = listOf(
        Triple("home", "首页", Icons.Default.Home),
        Triple("search", "搜索", Icons.Default.Search),
        Triple("playlists", "歌单", Icons.Default.PlaylistPlay)
    )
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: "home"

    val app = LocalContext.current.applicationContext as LxApp
    val currentSong by app.playerManager.currentSong.collectAsState()

    Scaffold(
        bottomBar = {
            Column {
                if (currentSong != null) {
                    MiniPlayer(onClick = onPlayerClick)
                }
                NavigationBar {
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
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(navController = navController, startDestination = "home") {
                composable("home") { HomeScreen(onAlbumClick = onAlbumClick) }
                composable("search") {
                    SearchScreen(
                        onAlbumClick = onAlbumClick,
                        onPlay = onPlayerClick
                    )
                }
                composable("playlists") {
                    PlaylistsScreen(onPlay = onPlayerClick)
                }
            }
        }
    }
}
