package com.steamdeck.mobile.presentation.navigation

/**
 * App-wide navigation screen definitions
 */
sealed class Screen(val route: String) {
 // Top-level screens (Bottom Navigation)
 data object Home : Screen("home?showAddGame={showAddGame}") {
  fun createRoute(showAddGame: Boolean = false): String {
   return if (showAddGame) {
    "home?showAddGame=true"
   } else {
    "home"
   }
  }
 }
 data object Downloads : Screen("downloads")
 data object Settings : Screen("settings?section={section}") {
  fun createRoute(section: Int? = null): String {
   return if (section != null) {
    "settings?section=$section"
   } else {
    "settings"
   }
  }
 }

 // Detail screens
 data object GameDetail : Screen("game/{gameId}") {
  fun createRoute(gameId: Long) = "game/$gameId"
 }

 // Game settings screen
 data object GameSettings : Screen("game/{gameId}/settings") {
  fun createRoute(gameId: Long) = "game/$gameId/settings"
 }

 // Settings sub-screens
 data object ControllerSettings : Screen("settings/controller")
 data object WineTest : Screen("settings/wine_test")
 data object SteamLogin : Screen("settings/steam_login")
 data object ContainerManagement : Screen("settings/containers")
}

/**
 * Navigation items (for Bottom Navigation / Navigation Rail)
 */
sealed class TopLevelDestination(
 val route: String,
 val iconResourceName: String,
 val labelResourceKey: String
) {
 data object Home : TopLevelDestination(
  route = "home", // Base route without arguments for bottom nav
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
