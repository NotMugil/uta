package com.notmugil.uta.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import com.notmugil.uta.ui.theme.UtaTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.composable
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost

@PreviewScreenSizes
@Composable
fun UtaApp() {
    UtaTheme {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        NavigationSuiteScaffold(
            navigationSuiteItems = {
                NavItems.entries.forEach {
                    item(
                        icon = {
                            Icon(
                                imageVector = it.icon,
                                contentDescription = it.label
                            )
                        },
                        label = { Text(it.label) },
                        selected = currentRoute == it.name,
                        onClick = {
                            navController.navigate(it.name) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                val screenModifier = Modifier.padding(innerPadding)
                NavHost(
                    navController = navController,
                    startDestination = NavItems.HOME.name,
                    modifier = screenModifier
                ) {
                    composable(NavItems.HOME.name) { HomeScreen() }
                    composable(route = NavItems.SEARCH.name) { SearchScreen() }
                    composable(NavItems.LIBRARY.name) { LibraryScreen() }
                    composable(NavItems.SETTINGS.name) { SettingsScreen() }
                    composable(route = "LOGIN") {
                        LoginScreen(onLoginSuccess = {
                            navController.navigate(NavItems.HOME.name) {
                                popUpTo("LOGIN") { inclusive = true }
                            }
                        })
                    }
                    composable(route = "ALBUM") { AlbumPage() }
                }
            }
        }
    }
}

enum class NavItems(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Rounded.Home),
    SEARCH("Search", Icons.Rounded.Search),
    LIBRARY("Library", Icons.Rounded.LibraryMusic),
    SETTINGS(label = "Settings", Icons.Rounded.Settings),
}
