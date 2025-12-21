package com.steamdeck.mobile.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.steamdeck.mobile.presentation.ui.settings.SettingsScreen
import com.steamdeck.mobile.presentation.viewmodel.ContainerViewModel
import com.steamdeck.mobile.presentation.viewmodel.SteamLoginViewModel

/**
 * App-wide navigation graph
 *
 * @param navController Navigation controller for managing app navigation
 * @param onOpenDrawer Callback to open the app-level drawer (triggered from Home screen hamburger menu)
 * @param modifier Modifier for the NavHost
 * @param startDestination Starting destination route
 */
@Composable
fun SteamDeckNavHost(
 navController: NavHostController,
 onOpenDrawer: () -> Unit = {},
 modifier: Modifier = Modifier,
 startDestination: String = Screen.Home.route
) {
 NavHost(
  navController = navController,
  startDestination = startDestination,
  modifier = modifier
 ) {
  // Top-level screen: Library (Home)
  composable(
   route = Screen.Home.route,
   arguments = listOf(
    navArgument("showAddGame") {
     type = NavType.BoolType
     defaultValue = false
    }
   ),
   enterTransition = {
    slideIntoContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.Start,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(300))
   },
   exitTransition = {
    slideOutOfContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.Start,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(300))
   },
   popEnterTransition = {
    slideIntoContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.End,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(300))
   },
   popExitTransition = {
    slideOutOfContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.End,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(300))
   }
  ) { backStackEntry ->
   val showAddGameFromNav = backStackEntry.arguments?.getBoolean("showAddGame") ?: false
   HomeScreen(
    onGameClick = { gameId ->
     navController.navigate(Screen.GameDetail.createRoute(gameId))
    },
    onOpenDrawer = onOpenDrawer,
    showAddGameDialogInitially = showAddGameFromNav
   )
  }

  // Top-level screen: Downloads
  composable(
   route = Screen.Downloads.route,
   enterTransition = {
    slideIntoContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.Start,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(300))
   },
   exitTransition = {
    slideOutOfContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.Start,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(300))
   },
   popEnterTransition = {
    slideIntoContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.End,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(300))
   },
   popExitTransition = {
    slideOutOfContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.End,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(300))
   }
  ) {
   DownloadScreen(
    onNavigateBack = {
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
     defaultValue = -1
    }
   ),
   enterTransition = {
    slideIntoContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.Start,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(300))
   },
   exitTransition = {
    slideOutOfContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.Start,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(300))
   },
   popEnterTransition = {
    slideIntoContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.End,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(300))
   },
   popExitTransition = {
    slideOutOfContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.End,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(300))
   }
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
   ),
   enterTransition = {
    slideIntoContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.Start,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(300))
   },
   exitTransition = {
    slideOutOfContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.Start,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(300))
   },
   popEnterTransition = {
    slideIntoContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.End,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(300))
   },
   popExitTransition = {
    slideOutOfContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.End,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(300))
   }
  ) { backStackEntry ->
   val gameId = backStackEntry.arguments?.getLong("gameId") ?: 0L
   GameDetailScreen(
    gameId = gameId,
    onNavigateBack = { navController.popBackStack() },
    onNavigateToSettings = {
     navController.navigate(Screen.Settings.route)
    }
   )
  }

  // Settings sub-screen: Controller settings
  composable(
   route = Screen.ControllerSettings.route,
   enterTransition = {
    slideIntoContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.Start,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(300))
   },
   exitTransition = {
    slideOutOfContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.Start,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(300))
   },
   popEnterTransition = {
    slideIntoContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.End,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(300))
   },
   popExitTransition = {
    slideOutOfContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.End,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(300))
   }
  ) {
   ControllerSettingsScreen(
    onBackClick = { navController.popBackStack() }
   )
  }

  // Settings sub-screen: Container Management
  composable(
   route = Screen.ContainerManagement.route,
   enterTransition = {
    slideIntoContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.Start,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(300))
   },
   exitTransition = {
    slideOutOfContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.Start,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(300))
   },
   popEnterTransition = {
    slideIntoContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.End,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(300))
   },
   popExitTransition = {
    slideOutOfContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.End,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(300))
   }
  ) {
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
  composable(
   route = Screen.SteamLogin.route,
   enterTransition = {
    slideIntoContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.Start,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(300))
   },
   exitTransition = {
    slideOutOfContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.Start,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(300))
   },
   popEnterTransition = {
    slideIntoContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.End,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(300))
   },
   popExitTransition = {
    slideOutOfContainer(
     towards = AnimatedContentTransitionScope.SlideDirection.End,
     animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(300))
   }
  ) {
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
