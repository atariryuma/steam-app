package com.steamdeck.mobile.presentation.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/**
 * SteamDeck Mobileアプリのメインコンポーネント
 *
 * Bottom Navigationを使用したナビゲーションを実装
 */
@Composable
fun SteamDeckApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    TopLevelDestination.all.forEach { destination ->
                        val isSelected = currentDestination?.hierarchy?.any {
                            it.route == destination.route
                        } == true

                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                // 同じ画面への重複ナビゲーションを防ぐ
                                if (!isSelected) {
                                    navController.navigate(destination.route) {
                                        // トップレベル画面へのナビゲーション時、バックスタックをクリア
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.labelResourceKey
                                )
                            },
                            label = { Text(destination.labelResourceKey) }
                        )
                    }
                }
            }
        ) { paddingValues ->
            SteamDeckNavHost(
                navController = navController,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

/**
 * トップレベルDestinationのアイコン取得
 */
private val TopLevelDestination.icon: ImageVector
    get() = when (this) {
        TopLevelDestination.Home -> Icons.Default.Home
        TopLevelDestination.Downloads -> Icons.Default.FileDownload
        TopLevelDestination.Settings -> Icons.Default.Settings
    }
