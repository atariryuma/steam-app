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
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import com.steamdeck.mobile.presentation.ui.download.DownloadScreen
import com.steamdeck.mobile.presentation.ui.game.GameDetailScreen
import com.steamdeck.mobile.presentation.ui.home.HomeScreen
import com.steamdeck.mobile.presentation.ui.container.ContainerScreen
import com.steamdeck.mobile.presentation.ui.settings.ControllerSettingsScreen
// import com.steamdeck.mobile.presentation.ui.settings.GameSettingsScreen // Temporarily disabled
import com.steamdeck.mobile.presentation.ui.settings.SettingsScreen
import com.steamdeck.mobile.presentation.viewmodel.ContainerViewModel
import com.steamdeck.mobile.presentation.viewmodel.SteamLoginViewModel

/**
 * App-wide navigation graph
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
  // Top-level screen: Library (Home)
  composable(Screen.Home.route) {
   val currentRoute = navController.currentBackStackEntry?.destination?.route ?: "home"
   HomeScreen(
    onGameClick = { gameId ->
     navController.navigate(Screen.GameDetail.createRoute(gameId))
    },
    onNavigateToSettings = {
     navController.navigate(Screen.Settings.route)
    },
    onNavigateToDownloads = {
     navController.navigate(Screen.Downloads.route)
    },
    onNavigateToSteamLogin = {
     navController.navigate(Screen.SteamLogin.route)
    },
    onNavigateToController = {
     navController.navigate(Screen.ControllerSettings.route)
    },
    onNavigateToContainerManagement = {
     navController.navigate(Screen.ContainerManagement.route)
    },
    onNavigateToSteamClient = {
     navController.navigate(Screen.Settings.createRoute(section = 1))
    },
    onNavigateToAppSettings = {
     navController.navigate(Screen.Settings.createRoute(section = 5))
    },
    currentRoute = currentRoute
   )
  }

  // Top-level screen: Downloads
  composable(Screen.Downloads.route) {
   DownloadScreen(
    onNavigateBack = {
     // Back navigation not required when accessing from Bottom Navigation
     // Also handles back navigation from detail screens
     if (navController.previousBackStackEntry != null) {
      navController.popBackStack()
     }
    }
   )
  }

  // Top-level screen: Settings
  composable(
   route = Screen.Settings.route,
   arguments = listOf(
    navArgument("section") {
     type = NavType.IntType
     defaultValue = -1  // -1 means no specific section
    }
   )
  ) { backStackEntry ->
   val settingsViewModel: com.steamdeck.mobile.presentation.viewmodel.SettingsViewModel = hiltViewModel()
   val initialSection = backStackEntry.arguments?.getInt("section") ?: -1

   SettingsScreen(
    viewModel = settingsViewModel,
    initialSection = initialSection,
    onNavigateBack = {
     if (navController.previousBackStackEntry != null) {
      navController.popBackStack()
     }
    },
    onNavigateToControllerSettings = {
     navController.navigate(Screen.ControllerSettings.route)
    }
   )
  }

  // Detail screen: Game details
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
     // Navigate to main Settings screen instead of game-specific settings (not yet implemented)
     navController.navigate(Screen.Settings.route)
    }
   )
  }

  // Detail screen: Game settings
  // TODO: Needs fixing - GameSettingsUiState not yet defined
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

  // Settings sub-screen: Controller settings
  composable(Screen.ControllerSettings.route) {
   ControllerSettingsScreen(
    onBackClick = { navController.popBackStack() }
   )
  }

  // Settings sub-screen: Container Management
  composable(Screen.ContainerManagement.route) {
   val containerViewModel: ContainerViewModel = hiltViewModel()

   ContainerScreen(
    viewModel = containerViewModel,
    onNavigateBack = {
     if (navController.previousBackStackEntry != null) {
      navController.popBackStack()
     }
    }
   )
  }

  // Settings sub-screen: Steam Login (OpenID authentication)
  composable(Screen.SteamLogin.route) {
   val loginViewModel: SteamLoginViewModel = hiltViewModel()
   val uiState by loginViewModel.uiState.collectAsState()

   // Generate authentication URL
   val (authUrl, _) = remember { loginViewModel.startOpenIdLogin() }

   // Steam OpenID login screen
   com.steamdeck.mobile.presentation.ui.auth.SteamOpenIdLoginScreen(
    authUrl = authUrl,
    callbackUrl = "http://127.0.0.1:8080/auth/callback",
    onAuthCallback = { callbackUrl -> loginViewModel.handleCallback(callbackUrl) },
    onError = { errorMessage -> android.util.Log.e("Navigation", "OpenID error: $errorMessage") }
   )

   // Handle authentication success
   val isSuccess = uiState is com.steamdeck.mobile.presentation.viewmodel.SteamLoginUiState.Success
   LaunchedEffect(isSuccess) {
    if (isSuccess) {
     android.util.Log.i(
      "SteamDeckNavHost",
      "✅ Steam authentication success! Navigating back to settings..."
     )
     navController.popBackStack()
    }
   }
  }
 }
}
