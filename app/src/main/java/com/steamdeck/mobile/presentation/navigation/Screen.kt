package com.steamdeck.mobile.presentation.navigation

/**
 * アプリ内 ナビゲーション画面定義
 */
sealed class Screen(val route: String) {
 // トップレベル画面（Bottom Navigation）
 data object Home : Screen("home")
 data object Downloads : Screen("downloads")
 data object Settings : Screen("settings")

 // 詳細画面
 data object GameDetail : Screen("game/{gameId}") {
  fun createRoute(gameId: Long) = "game/$gameId"
 }

 // gamesettings画面
 data object GameSettings : Screen("game/{gameId}/settings") {
  fun createRoute(gameId: Long) = "game/$gameId/settings"
 }

 // settingsサブ画面
 data object ControllerSettings : Screen("settings/controller")
 data object WineTest : Screen("settings/wine_test")
 data object SteamLogin : Screen("settings/steam_login")
 data object ContainerManagement : Screen("settings/containers")
}

/**
 * ナビゲーションアイテム（Bottom Navigation/Navigation Rail用）
 */
sealed class TopLevelDestination(
 val route: String,
 val iconResourceName: String,
 val labelResourceKey: String
) {
 data object Home : TopLevelDestination(
  route = Screen.Home.route,
  iconResourceName = "home",
  labelResourceKey = "library"
 )

 data object Downloads : TopLevelDestination(
  route = Screen.Downloads.route,
  iconResourceName = "download",
  labelResourceKey = "download"
 )

 data object Settings : TopLevelDestination(
  route = Screen.Settings.route,
  iconResourceName = "settings",
  labelResourceKey = "settings"
 )

 companion object {
  val all = listOf(Home, Downloads, Settings)
 }
}
