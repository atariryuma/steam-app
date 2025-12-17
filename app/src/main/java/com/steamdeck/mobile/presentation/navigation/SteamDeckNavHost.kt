package com.steamdeck.mobile.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.steamdeck.mobile.presentation.ui.download.DownloadScreen
import com.steamdeck.mobile.presentation.ui.game.GameDetailScreen
import com.steamdeck.mobile.presentation.ui.home.HomeScreen
import com.steamdeck.mobile.presentation.ui.settings.ControllerSettingsScreen
import com.steamdeck.mobile.presentation.ui.settings.SettingsScreen
import com.steamdeck.mobile.presentation.ui.wine.WineTestScreen

/**
 * アプリ全体のナビゲーショングラフ
 */
@Composable
fun SteamDeckNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // トップレベル画面：ライブラリ（ホーム）
        composable(Screen.Home.route) {
            HomeScreen(
                onGameClick = { gameId ->
                    navController.navigate(Screen.GameDetail.createRoute(gameId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        // トップレベル画面：ダウンロード
        composable(Screen.Downloads.route) {
            DownloadScreen(
                onNavigateBack = {
                    // Bottom Navigationからアクセス時はBack不要
                    // 詳細画面からの戻りにも対応
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                }
            )
        }

        // トップレベル画面：設定
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                },
                onNavigateToWineTest = {
                    navController.navigate(Screen.WineTest.route)
                },
                onNavigateToControllerSettings = {
                    navController.navigate(Screen.ControllerSettings.route)
                }
            )
        }

        // 詳細画面：ゲーム詳細
        composable(
            route = Screen.GameDetail.route,
            arguments = listOf(
                navArgument("gameId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val gameId = backStackEntry.arguments?.getLong("gameId") ?: 0L
            GameDetailScreen(
                gameId = gameId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 設定サブ画面：コントローラー設定
        composable(Screen.ControllerSettings.route) {
            ControllerSettingsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // 設定サブ画面：Wineテスト
        composable(Screen.WineTest.route) {
            WineTestScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
