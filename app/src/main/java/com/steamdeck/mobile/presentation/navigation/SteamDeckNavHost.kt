package com.steamdeck.mobile.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.steamdeck.mobile.presentation.ui.auth.SteamLoginScreen
import com.steamdeck.mobile.presentation.ui.download.DownloadScreen
import com.steamdeck.mobile.presentation.ui.game.GameDetailScreen
import com.steamdeck.mobile.presentation.ui.home.HomeScreen
import com.steamdeck.mobile.presentation.ui.settings.ControllerSettingsScreen
// import com.steamdeck.mobile.presentation.ui.settings.GameSettingsScreen // Temporarily disabled
import com.steamdeck.mobile.presentation.ui.settings.SettingsScreen
import com.steamdeck.mobile.presentation.ui.wine.WineTestScreen
import com.steamdeck.mobile.presentation.viewmodel.SteamLoginViewModel

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
            val settingsViewModel: com.steamdeck.mobile.presentation.viewmodel.SettingsViewModel = hiltViewModel()

            SettingsScreen(
                viewModel = settingsViewModel,
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
                },
                onNavigateToSteamLogin = {
                    navController.navigate(Screen.SteamLogin.route)
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
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSettings = {
                    navController.navigate(Screen.GameSettings.createRoute(gameId))
                }
            )
        }

        // 詳細画面：ゲーム設定
        // TODO: 修正が必要 - GameSettingsUiStateが未定義
        /*
        composable(
            route = Screen.GameSettings.route,
            arguments = listOf(
                navArgument("gameId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val gameId = backStackEntry.arguments?.getLong("gameId") ?: 0L
            GameSettingsScreen(
                gameId = gameId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        */

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

        // 設定サブ画面：Steam ログイン（新UI）
        composable(Screen.SteamLogin.route) {
            val loginViewModel: SteamLoginViewModel = hiltViewModel()
            val settingsViewModel: com.steamdeck.mobile.presentation.viewmodel.SettingsViewModel = hiltViewModel()
            val uiState by loginViewModel.uiState.collectAsState()
            val qrCodeBitmap by loginViewModel.qrCodeBitmap.collectAsState()

            // 画面表示時に一度だけQRコードログインを開始
            LaunchedEffect(Unit) {
                if (uiState is com.steamdeck.mobile.presentation.viewmodel.SteamLoginUiState.Initial) {
                    loginViewModel.startQrCodeLogin()
                }
            }

            com.steamdeck.mobile.presentation.ui.auth.SteamStyleLoginScreen(
                qrCodeBitmap = qrCodeBitmap,
                isLoading = uiState is com.steamdeck.mobile.presentation.viewmodel.SteamLoginUiState.Loading,
                errorMessage = (uiState as? com.steamdeck.mobile.presentation.viewmodel.SteamLoginUiState.Error)?.message,
                onLogin = { email, password, rememberMe ->
                    loginViewModel.loginWithCredentials(email, password, rememberMe)
                },
                onQrCodeLogin = {
                    // 手動でQRコード表示をリクエストした場合
                    // （通常は自動的に開始されるため不要）
                },
                onForgotPassword = {
                    // TODO: パスワード忘れ実装（ブラウザで開く）
                    // val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://help.steampowered.com/"))
                    // context.startActivity(intent)
                },
                onRegenerateQrCode = {
                    loginViewModel.regenerateQrCode()
                },
                onNavigateBack = { navController.popBackStack() },
                onErrorDismiss = {
                    loginViewModel.clearError()
                }
            )

            // 認証成功時の処理（トークンは既にViewModelで保存済み）
            // 🔥 修正: uiStateがdata object Successの場合、同じインスタンスなので
            // Boolean値に変換してLaunchedEffectのキーにする
            val isSuccess = uiState is com.steamdeck.mobile.presentation.viewmodel.SteamLoginUiState.Success
            LaunchedEffect(isSuccess) {
                if (isSuccess) {
                    android.util.Log.i(
                        "SteamDeckNavHost",
                        "✅ Steam authentication success detected! Triggering auto-sync and navigation..."
                    )
                    // 自動ライブラリ同期をトリガー
                    settingsViewModel.syncAfterQrLogin()
                    navController.popBackStack()
                }
            }
        }
    }
}
